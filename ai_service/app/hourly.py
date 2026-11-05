"""시간대 비율표(models/ALL_hourly.json)를 읽어 (노선, 날 유형)의 24시간 비율을 돌려준다.

시간대별 예측 = 그날의 일별 예측값 x 시각 비율. 비율표는 개인 프로젝트에서 시간대별 원본을 모아 만들고, 여기에는 만들어진 JSON 파일만 있다.
JSON 이라 읽어도 코드가 실행되지 않는다(모델 pickle 과 다름).
- 노선의 표본(승차 합) n 이 작으면 노선 비율에 전체 평균 비율을 섞는다: 가중치 w = n / (n + k).
- 해당 날 유형의 자료가 아예 없으면 가까운 유형으로 대신한다(FALLBACK). 응답의 basis 로 알 수 있다.
"""
import json
import logging
import math
import threading
from pathlib import Path

log = logging.getLogger("hourly")

HOURS = 24
FALLBACK = {0: (0,), 1: (1, 2, 0), 2: (2, 1, 0), 3: (3, 2, 1, 0)}   # 날 유형 -> 찾아볼 순서(0 평일 1 토 2 일·공휴일 3 명절)
FILE_NAME = "{scope}_hourly.json"
MIN_KIND_DAYS = 3                                    # 날 유형별로 이 날짜 수 미만이면 비율 근거가 약하다고 표시(limited)
WEAK_KINDS = frozenset({3})                          # 명절은 자료가 3일뿐이고 검증 오차가 약 27%(평일 약 13%)라 날짜 수와 관계없이 항상 limited


class HourlyError(Exception):
    pass


class HourlyShares:
    def __init__(self, data: dict):
        try:
            if data["version"] != 1:
                raise HourlyError(f"지원하지 않는 비율표 버전입니다: {data['version']}")
            self.k = float(data["k"])
            self.provisional = bool(data.get("provisional", False))
            self.built_from = dict(data["builtFrom"])
            self.kind_days = {int(k): int(v) for k, v in data.get("dayKindDays", {}).items()}   # 날 유형별로 비율을 만든 날짜 수
            self.global_ = {int(k): self._check(v, f"전체 {k}") for k, v in data["global"].items()}
            self.routes = {str(r): {int(k): (float(v["n"]), self._check(v["share"], f"{r}/{k}")) for k, v in kinds.items()}
                           for r, kinds in data["routes"].items()}
        except HourlyError:
            raise
        except (KeyError, TypeError, ValueError) as e:
            raise HourlyError(f"비율표 형식이 올바르지 않습니다: {type(e).__name__}: {e}") from e
        if not self.global_ or not self.k > 0:
            raise HourlyError("비율표에 전체 평균 비율이 없거나 k 가 올바르지 않습니다")

    @staticmethod
    def _check(values, what: str) -> list[float]:
        v = [float(x) for x in values]
        if len(v) != HOURS or any(x < 0 or not math.isfinite(x) for x in v) or abs(sum(v) - 1.0) > 1e-3:
            raise HourlyError(f"비율이 올바르지 않습니다({what}): 24개, 0 이상, 합 1 이어야 합니다")
        return v

    def shares(self, route_no: str, day_kind: int) -> tuple[list[float], str, int, bool]:
        """(24시간 비율, basis, 실제로 쓴 날 유형, limited).
        basis: route(노선 표본이 충분) / blend(전체 평균을 섞음) / global(노선 자료 없음).
        limited: 요청한 날 유형 대신 다른 유형을 썼거나, 쓴 유형의 날짜 수가 MIN_KIND_DAYS 미만이거나 명절(WEAK_KINDS)이라 근거가 약함(화면이 경고)."""
        for kind in FALLBACK.get(day_kind, (0,)):
            if kind not in self.global_:
                continue
            limited = kind != day_kind or kind in WEAK_KINDS or self.kind_days.get(kind, 0) < MIN_KIND_DAYS
            glob = self.global_[kind]
            hit = self.routes.get(route_no, {}).get(kind)
            if hit is None:
                return glob, "global", kind, limited
            n, share = hit
            w = n / (n + self.k)
            mixed = [w * s + (1 - w) * g for s, g in zip(share, glob)]
            return mixed, ("route" if w >= 0.9 else "blend"), kind, limited
        raise HourlyError("사용할 수 있는 날 유형 비율이 없습니다")

    @classmethod
    def load(cls, path: Path) -> "HourlyShares":
        try:
            return cls(json.loads(path.read_text(encoding="utf-8")))
        except (OSError, json.JSONDecodeError) as e:
            raise HourlyError(f"비율표를 읽지 못했습니다: {type(e).__name__}: {e}") from e


class HourlyHolder:
    """비율표를 들고 있다가 파일이 바뀌면 다시 읽는다. 새 파일이 깨졌으면 이전 것을 유지한다(ModelHolder 와 같은 방식)."""

    def __init__(self, directory: Path, scope: str):
        self.path = directory / FILE_NAME.format(scope=scope)
        self._lock = threading.Lock()
        self._data: HourlyShares | None = None
        self._signature: tuple | None = None
        self.last_error: str | None = None

    def _sig(self) -> tuple | None:
        try:
            st = self.path.stat()
            return (st.st_mtime_ns, st.st_size)
        except OSError:
            return None

    def get(self) -> HourlyShares | None:
        sig = self._sig()
        if sig is not None and sig == self._signature:
            return self._data
        with self._lock:
            sig = self._sig()
            if sig is None:
                self.last_error = "시간대 비율표 파일이 없습니다"
                return self._data
            if sig != self._signature:
                try:
                    self._data, self.last_error = HourlyShares.load(self.path), None
                    log.info("시간대 비율표를 불러왔습니다: %s", self.path.name)
                except HourlyError as e:
                    self.last_error = str(e)
                    log.warning("새 비율표를 불러오지 못해 이전 것을 유지합니다: %s", e)
                self._signature = sig
            return self._data
