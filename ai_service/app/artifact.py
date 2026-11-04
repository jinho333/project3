"""학습된 모델을 파일로 저장·불러오기 (REST 즉석 예측용). 저장 → 불러오기 → 같은 예측이 나와야 한다.

파일 하나(`<범위>.joblib`)에 모델과 예측에 필요한 모든 것을 담는다: 학습된 모델, 학습 기간, 예측 가능한 노선 목록,
노선별 공급 변수(배차 횟수·정류장 수) 스냅샷. 그래서 예측할 때 DB 없이 (노선번호, 날짜)만 받아도 입력 변수를 만들 수 있다.
옆의 `<범위>.json`은 사람이 읽는 요약(모델 이름·기간·오차·해시)이며 불러올 때 파일이 깨지지 않았는지 해시로 확인한다.

**보안 주의**: joblib 파일은 pickle이라 **불러오는 것이 곧 코드 실행**이다. 이 프로그램이 만든 파일만, 지정한 폴더 안에서만 불러오고
남이 보낸 파일·내려받은 파일은 절대 불러오지 않는다(해시는 파일이 깨졌는지 확인할 뿐 악의적인 변조를 막지 못한다).
폴더 쓰기 권한이 있는 사람은 곧 서버에서 코드를 실행할 수 있는 사람이다.
"""
import hashlib
import json
import os
import re
import sys
import tempfile
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from pathlib import Path

import joblib
import pandas as pd

from app import features
from app.candidates import HORIZON_DAYS, Fitted

FORMAT_VERSION = 1
MAX_HORIZON_DAYS = HORIZON_DAYS   # 학습 마지막 날 이후로 이만큼보다 먼 날짜는 예측하지 않는다(최종 시험으로 검증한 거리까지만)
SCOPE_RE = re.compile(r"^[A-Za-z0-9_-]{1,20}$")
SUPPLY_COLS = ["route_no", "trips_0", "trips_1", "trips_2", "stop_count", "route_count"]


class ArtifactError(Exception):
    """파일이 없거나, 깨졌거나, 형식이 맞지 않음"""


class PredictInputError(ValueError):
    """예측 요청이 잘못됨(모르는 노선, 범위를 벗어난 날짜 등). REST에서는 4xx로 돌려준다"""


class UnknownRouteError(PredictInputError):
    """학습에 없던 노선(REST에서는 404)"""


@dataclass
class ModelBundle:
    model_name: str
    label: str
    scope: str                       # ALL 또는 구 코드(중간 결과)
    trained_start: date
    trained_end: date
    trained_rows: int
    routes: list[str]                # 예측 가능한 노선번호(학습에 있던 노선)
    supply: pd.DataFrame             # route_no + trips_0/1/2 + stop_count + route_count (노선번호당 1행)
    fitted: Fitted
    model_version: str
    champion_basis: str | None = None
    holdout_wape: float | None = None
    created_at: str = field(default_factory=lambda: datetime.now().isoformat(timespec="seconds"))
    format_version: int = FORMAT_VERSION

    def horizon_end(self) -> date:
        return self.trained_end + timedelta(days=MAX_HORIZON_DAYS)

    def predict(self, route_nos: list[str], dates: list[date]) -> pd.DataFrame:
        """(노선번호 x 날짜) 예측. 반환: route_no, basic_day, predicted(명, 0 이상 소수).

        학습에 없던 노선, 학습 기간 이전이거나 학습 마지막 날로부터 MAX_HORIZON_DAYS를 넘는 날짜는 PredictInputError.
        """
        if not route_nos or not dates:
            raise PredictInputError("노선과 날짜를 하나 이상 지정하세요.")
        routes = [str(r) for r in route_nos]
        unknown = sorted(set(routes) - set(self.routes))
        if unknown:
            raise UnknownRouteError(f"예측할 수 없는 노선입니다(학습에 없던 노선): {', '.join(unknown[:5])}")
        first, last = min(dates), max(dates)
        if first < self.trained_start or last > self.horizon_end():
            raise PredictInputError(
                f"날짜는 {self.trained_start} ~ {self.horizon_end()} 사이여야 합니다(학습 마지막 날 {self.trained_end} + {MAX_HORIZON_DAYS}일).")
        supply = self.supply[self.supply["route_no"].isin(routes)]
        frame = features.daily_features(features.calendar_features(first, last), supply)
        frame = frame[frame["basic_day"].isin(set(dates))].reset_index(drop=True)
        frame["route_no"] = frame["route_no"].astype(str)
        out = frame[["route_no", "basic_day"]].copy()
        out["predicted"] = self.fitted.predict(frame).to_numpy()
        return out

    def summary(self) -> dict:
        return {"model_name": self.model_name, "label": self.label, "scope": self.scope, "model_version": self.model_version,
                "trained_start": self.trained_start.isoformat(), "trained_end": self.trained_end.isoformat(),
                "trained_rows": self.trained_rows, "route_count": len(self.routes), "champion_basis": self.champion_basis,
                "holdout_wape": self.holdout_wape, "created_at": self.created_at, "format_version": self.format_version,
                "predict_until": self.horizon_end().isoformat()}


def model_paths(directory: Path, scope: str) -> tuple[Path, Path]:
    if not SCOPE_RE.match(scope):
        raise ArtifactError(f"범위 이름이 올바르지 않습니다: {scope!r}")
    return directory / f"{scope}.joblib", directory / f"{scope}.json"


# 모델 파일(pickle)은 만든 환경의 라이브러리 버전에 묶여 있다. 다른 버전에서 열면 경고 없이 결과가 달라지거나 깨질 수 있어 기록해 둔다.
TRACKED_LIBRARIES = ("scikit-learn", "numpy", "pandas", "xgboost", "lightgbm", "joblib")


def runtime_versions() -> dict:
    """지금 실행 중인 환경의 파이썬·주요 라이브러리 버전 (설치되지 않은 것은 생략)."""
    from importlib import metadata
    out = {"python": ".".join(map(str, sys.version_info[:3]))}
    for name in TRACKED_LIBRARIES:
        try:
            out[name] = metadata.version(name)
        except metadata.PackageNotFoundError:
            pass
    return out


def version_mismatches(saved: dict | None, current: dict | None = None) -> list[str]:
    """저장 당시와 지금의 버전이 (앞 두 자리 기준) 다른 항목 설명. 기록이 없는 옛 파일은 확인할 수 없다는 안내 하나를 돌려준다."""
    if not saved:
        return ["모델 파일에 만든 환경의 라이브러리 버전 기록이 없습니다(예전 형식). 다시 저장하면 기록됩니다."]
    now = current if current is not None else runtime_versions()
    short = lambda v: ".".join(str(v).split(".")[:2])  # noqa: E731
    return [f"{name}: 저장 {saved[name]} / 현재 {now.get(name, '없음')}" for name in saved
            if name != "joblib" and short(saved[name]) != short(now.get(name, ""))]


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def save_bundle(bundle, directory: Path) -> Path:
    """예측 모델 묶음(ModelBundle) 저장. 범용 저장은 save_bundle_as."""
    return save_bundle_as(bundle, directory, bundle.scope, bundle.summary())


def save_bundle_as(obj, directory: Path, scope: str, summary: dict) -> Path:
    """객체와 요약을 저장한다. 임시 파일에 쓰고 이름을 바꿔서, 쓰는 도중에 읽는 쪽이 깨진 파일을 보지 않게 한다."""
    model_path, meta_path = model_paths(directory, scope)
    directory.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=directory, suffix=".tmp")
    os.close(fd)
    try:
        joblib.dump(obj, tmp, compress=3)
        digest = _sha256(Path(tmp))
        os.replace(tmp, model_path)
    except BaseException:
        Path(tmp).unlink(missing_ok=True)
        raise
    meta = {**summary, "library_versions": runtime_versions(), "file": model_path.name, "sha256": digest, "size_bytes": model_path.stat().st_size}
    fd, tmp_meta = tempfile.mkstemp(dir=directory, suffix=".tmp")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    os.replace(tmp_meta, meta_path)
    return model_path


def read_summary(directory: Path, scope: str) -> dict:
    """요약만 읽는다(모델은 불러오지 않음 — 상태 확인용)."""
    _, meta_path = model_paths(directory, scope)
    if not meta_path.is_file():
        raise ArtifactError(f"저장된 모델이 없습니다: {meta_path}")
    return json.loads(meta_path.read_text(encoding="utf-8"))


def load_bundle(directory: Path, scope: str, verify: bool = True, expected_type: type | None = None):
    """저장한 모델을 불러온다. 이 프로그램이 만든 파일만 불러올 것(모듈 머리말의 보안 주의)."""
    model_path, _ = model_paths(directory, scope)
    if not model_path.is_file():
        raise ArtifactError(f"저장된 모델이 없습니다: {model_path}")
    if verify:
        meta = read_summary(directory, scope)
        if meta.get("sha256") != _sha256(model_path):
            raise ArtifactError("모델 파일이 요약(해시)과 다릅니다. 파일이 깨졌거나 다시 학습하는 도중일 수 있습니다.")
    bundle = joblib.load(model_path)
    expected = expected_type or ModelBundle
    if not isinstance(bundle, expected) or getattr(bundle, "format_version", None) != FORMAT_VERSION:
        raise ArtifactError("지원하지 않는 모델 파일 형식입니다. 다시 학습해 저장하세요.")
    return bundle


def supply_snapshot(supply_by_route_no: pd.DataFrame) -> pd.DataFrame:
    """features.supply_by_route_no 결과에서 예측에 쓰는 열만 남긴다(노선번호당 1행)."""
    missing = [c for c in SUPPLY_COLS if c not in supply_by_route_no.columns]
    if missing:
        raise ValueError(f"공급 변수 열이 없습니다: {missing}")
    out = supply_by_route_no[SUPPLY_COLS].copy()
    out["route_no"] = out["route_no"].astype(str)
    return out.drop_duplicates("route_no").reset_index(drop=True)
