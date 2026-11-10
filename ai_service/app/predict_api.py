"""예측 API: 저장된 모델(models/ALL.joblib)로 (노선번호, 날짜)의 이용자수를 즉석 예측한다. 웹(Spring)이 내부에서 호출한다.
모델을 만드는 수집·학습 코드는 이 폴더에 없다(만든 사람의 로컬에서 관리). 여기에는 만들어진 모델로 예측하는 부분만 있다.

실행 (ai_service 디렉터리에서, 로컬 전용):
    PREDICT_API_KEY=<길고 무작위인 문자열> uvicorn app.predict_api:app --host 127.0.0.1 --port 8000

- 이 API는 **웹 서버가 부르는 내부용**이다. 브라우저가 직접 부르지 않고, 로컬(127.0.0.1) 요청만 받으며 X-API-Key 헤더가 맞아야 한다.
- 키(PREDICT_API_KEY)가 비어 있으면 시작하지 않는다(실수로 열린 API가 되는 것을 막음).
- 모델 파일이 새로 저장되면(다시 학습) 다음 요청에서 자동으로 바꿔 쓴다. 새 파일이 깨졌으면 이전 모델을 계속 쓴다.
- 합성데이터(국토교통부 노선별 이용량 AI 합성데이터)로 학습한 통계적 경향이며 개별 값의 정밀 예측이 아니다(응답 notice).
- 문서(OpenAPI)는 /docs 에 있다. 요청·응답 형식을 바꾸면 웹(Spring) 쪽 클라이언트도 함께 바꿀 것.
"""
import logging
import re
import secrets
import threading
from contextlib import asynccontextmanager
from datetime import date, timedelta
from pathlib import Path
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Query

from app import artifact
from app.api_models import HealthResponse, HourlyResponse, PredictResponse
from app.config import settings
from app.features import calendar_features
from app.hourly import HourlyHolder
from app.localonly import local_only
from app.api_models import RoutesResponse

log = logging.getLogger("predict_api")

MAX_DAYS = 31
MIN_KEY_LENGTH = 16                                   # 이보다 짧은 키는 거부(길고 무작위인 문자열을 쓰도록)
ROUTE_NO_RE = re.compile(r"^[0-9A-Za-z\-]{1,10}$")
NOTICE = "합성데이터 기반 통계적 경향 예측입니다(개별 값의 정밀 예측 아님)."
DAY_LABELS = "월화수목금토일"
HORIZON_WARN_DAYS = 28                                # 예측 가능한 마지막 날까지 이 일수 이하로 남으면 /health 에서 알린다(재학습할 시간을 두기 위해)


def horizon_warning(horizon_end: date, today: date) -> str | None:
    """예측 가능한 마지막 날(horizon_end)이 가까워졌거나 지났으면 안내 문구, 아니면 None. 날짜 비교만 하므로 today 를 직접 넣어 시험한다."""
    left = (horizon_end - today).days
    if left < 0:
        return f"예측 가능 기간이 끝났습니다(마지막 날 {horizon_end}). 이 날짜 이후는 모든 노선이 예측되지 않으니 모델을 다시 학습해 저장해야 합니다."
    if left <= HORIZON_WARN_DAYS:
        when = "오늘이 마지막 날입니다" if left == 0 else f"{left}일 남았습니다"
        return f"예측 가능 기간이 곧 끝납니다: {when}(마지막 날 {horizon_end}). 모델을 다시 학습해 저장해야 합니다."
    return None


class ModelHolder:
    """저장된 모델을 들고 있다가 파일이 바뀌면 다시 불러온다(요청마다 파일 상태를 확인하는 비용은 stat 두 번)."""

    def __init__(self, directory: Path, scope: str, expected_type: type | None = None):
        self.directory, self.scope, self.expected_type = directory, scope, expected_type
        self._lock = threading.Lock()
        self._bundle = None
        self._signature: tuple | None = None
        self.last_error: str | None = None
        self.warnings: list[str] = []                 # 불러온 모델이 다른 환경(라이브러리 버전)에서 만들어졌을 때의 안내

    def _current_signature(self) -> tuple | None:
        try:
            model_path, meta_path = artifact.model_paths(self.directory, self.scope)
            return tuple((p.stat().st_mtime_ns, p.stat().st_size) for p in (model_path, meta_path))
        except (OSError, artifact.ArtifactError):
            return None

    def _check_versions(self) -> list[str]:
        try:
            found = artifact.version_mismatches(artifact.read_summary(self.directory, self.scope).get("library_versions"))
        except (OSError, ValueError, artifact.ArtifactError):
            return []
        for w in found:
            log.warning("모델 환경 확인(%s): %s", self.scope, w)
        return found

    def get(self):
        sig = self._current_signature()
        if sig is not None and sig == self._signature:
            return self._bundle
        with self._lock:
            sig = self._current_signature()
            if sig is None:
                self.last_error = "저장된 모델 파일이 없습니다"
                return self._bundle                    # 파일이 사라져도 이미 불러온 모델은 계속 쓴다
            if sig != self._signature:
                try:
                    self._bundle = artifact.load_bundle(self.directory, self.scope, expected_type=self.expected_type)
                    self._signature, self.last_error = sig, None
                    self.warnings = self._check_versions()
                    log.info("모델을 불러왔습니다: 범위 %s (%s)", self.scope, type(self._bundle).__name__)
                except Exception as e:                  # noqa: BLE001 - 파일이 쓰이는 도중이거나 깨졌을 수 있음: 이전 모델 유지
                    self.last_error = f"{type(e).__name__}: {e}"
                    self._signature = sig               # 같은 깨진 파일로 매 요청 다시 시도하지 않음(파일이 또 바뀌면 다시 시도)
                    log.warning("새 모델 파일을 불러오지 못해 이전 모델을 유지합니다: %s", self.last_error)
            return self._bundle


def create_app(holder: ModelHolder | None = None, api_key: str | None = None, hourly: HourlyHolder | None = None) -> FastAPI:
    key = settings.predict_api_key if api_key is None else api_key
    models = holder or ModelHolder(settings.model_dir, settings.model_scope)
    hourly_shares = hourly or HourlyHolder(models.directory, models.scope)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if not key:
            raise RuntimeError("PREDICT_API_KEY 환경변수가 비어 있습니다. 길고 무작위인 문자열로 설정한 뒤 다시 시작하세요(.env 확인).")
        if len(key) < MIN_KEY_LENGTH:
            raise RuntimeError(f"PREDICT_API_KEY 가 너무 짧습니다({MIN_KEY_LENGTH}자 이상 필요). 길고 무작위인 문자열로 바꾸세요.")
        models.get()                                    # 시작할 때 한 번 불러 둔다(없어도 시작은 함: /health에서 확인)
        yield

    app = FastAPI(title="Ulsan Bus Prediction API", version="1", lifespan=lifespan, dependencies=[Depends(local_only)])

    def require_key(x_api_key: Annotated[str | None, Header(alias="X-API-Key")] = None) -> None:
        if not x_api_key or not secrets.compare_digest(x_api_key.encode(), key.encode()):
            raise HTTPException(401, "API 키가 없거나 올바르지 않습니다.")

    @app.get("/health", response_model=HealthResponse)
    def health() -> dict:
        """상태 확인(키 없이 로컬에서). status: ok(예측 모델 사용 가능) / no_model(없음)."""
        bundle = models.get()
        expiry = None if bundle is None else horizon_warning(bundle.horizon_end(), date.today())
        return {"status": "ok" if bundle is not None else "no_model",
                "modelLoaded": bundle is not None,
                "model": None if bundle is None else {"name": bundle.model_name, "version": bundle.model_version,
                                                      "scope": bundle.scope, "trainedEnd": bundle.trained_end.isoformat()},
                "lastError": models.last_error,
                "warnings": [f"예측 모델 {w}" for w in models.warnings] + ([expiry] if expiry else [])}

    @app.get("/predict", dependencies=[Depends(require_key)], response_model=PredictResponse)
    def predict(route_no: Annotated[str, Query(alias="routeNo", description="노선번호 (예: 106)")],
                from_: Annotated[date, Query(alias="from", description="시작 날짜 YYYY-MM-DD")],
                to: Annotated[date | None, Query(description="끝 날짜(생략하면 시작 날짜 하루)")] = None,
                clamp: Annotated[bool, Query(description="true 면 끝 날짜가 예측 가능한 마지막 날을 넘을 때 그 날까지만 돌려준다(시작 날짜가 범위 밖이면 그대로 422)")] = False) -> dict:
        if not ROUTE_NO_RE.match(route_no):
            raise HTTPException(422, "노선번호 형식이 올바르지 않습니다.")
        end = to or from_
        if end < from_:
            raise HTTPException(422, "끝 날짜가 시작 날짜보다 빠릅니다.")
        if (end - from_).days + 1 > MAX_DAYS:
            raise HTTPException(422, f"한 번에 최대 {MAX_DAYS}일까지 예측할 수 있습니다.")
        bundle = models.get()
        if bundle is None:
            raise HTTPException(503, "저장된 모델이 없습니다. python -m app.model ... --save-model 로 모델을 저장하세요.")
        last_day = bundle.horizon_end()
        if clamp and from_ <= last_day < end:             # 오늘부터 N일 같은 창이 마지막 날을 걸칠 때, 앞쪽 유효한 날짜는 살린다
            end = last_day
        days = [from_ + timedelta(days=i) for i in range((end - from_).days + 1)]
        try:
            out = bundle.predict([route_no], days)
        except artifact.UnknownRouteError as e:
            raise HTTPException(404, str(e))
        except artifact.PredictInputError as e:
            raise HTTPException(422, str(e))
        return {
            "routeNo": route_no,
            "model": {"name": bundle.model_name, "label": bundle.label, "version": bundle.model_version, "scope": bundle.scope,
                      "trainedEnd": bundle.trained_end.isoformat(), "predictUntil": bundle.horizon_end().isoformat(),
                      "championBasis": bundle.champion_basis, "partial": bundle.scope != "ALL"},
            "predictions": [{"date": r.basic_day.isoformat(), "dayOfWeek": DAY_LABELS[r.basic_day.weekday()],
                             "predicted": int(round(r.predicted))} for r in out.itertuples()],
            "notice": NOTICE,
        }

    @app.get("/routes", dependencies=[Depends(require_key)], response_model=RoutesResponse)
    def routes() -> dict:
        """예측할 수 있는 노선번호 목록(모델이 학습한 노선, 번호순). 웹이 DB의 노선 표(BUS_ROUTE)와 맞출 때 쓴다."""
        bundle = models.get()
        if bundle is None:
            raise HTTPException(503, "저장된 모델이 없습니다. python -m app.model ... --save-model 로 모델을 저장하세요.")
        route_list = sorted(str(r) for r in bundle.routes)
        return {"modelVersion": bundle.model_version, "trainedEnd": bundle.trained_end.isoformat(),
                "count": len(route_list), "routes": route_list}

    @app.get("/predict/hourly", dependencies=[Depends(require_key)], response_model=HourlyResponse)
    def predict_hourly(route_no: Annotated[str, Query(alias="routeNo", description="노선번호 (예: 106)")],
                       day: Annotated[date, Query(alias="date", description="날짜 YYYY-MM-DD (하루)")]) -> dict:
        """그날의 일별 예측값에 (노선, 날 유형, 시각) 비율을 곱해 시간대별 예상 승차 인원을 돌려준다."""
        if not ROUTE_NO_RE.match(route_no):
            raise HTTPException(422, "노선번호 형식이 올바르지 않습니다.")
        bundle = models.get()
        if bundle is None:
            raise HTTPException(503, "저장된 모델이 없습니다. python -m app.model ... --save-model 로 모델을 저장하세요.")
        table = hourly_shares.get()
        if table is None:
            raise HTTPException(503, "시간대 비율표가 없습니다(models/ALL_hourly.json). 일별 예측(/predict)은 계속 쓸 수 있습니다.")
        try:
            daily = bundle.predict([route_no], [day])
        except artifact.UnknownRouteError as e:
            raise HTTPException(404, str(e))
        except artifact.PredictInputError as e:
            raise HTTPException(422, str(e))
        total = max(0.0, float(daily["predicted"].iloc[0]))
        kind = int(calendar_features(day, day)["day_kind"].iloc[0])
        shares, basis, used_kind, limited = table.shares(route_no, kind)
        return {
            "routeNo": route_no, "date": day.isoformat(), "dayOfWeek": DAY_LABELS[day.weekday()],
            "dailyPredicted": int(round(total)),
            "hours": [{"hour": h, "predicted": int(round(total * s))} for h, s in enumerate(shares)],
            "shareBasis": basis, "shareDayKind": used_kind, "limited": limited, "provisional": table.provisional,
            "model": {"name": bundle.model_name, "label": bundle.label, "version": bundle.model_version, "scope": bundle.scope,
                      "trainedEnd": bundle.trained_end.isoformat(), "predictUntil": bundle.horizon_end().isoformat(),
                      "championBasis": bundle.champion_basis, "partial": bundle.scope != "ALL"},
            "notice": NOTICE,
        }

    return app


app = create_app()
