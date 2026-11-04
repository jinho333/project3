"""예측 API의 응답 형식. 웹(Spring) 쪽 클라이언트(RestClient)가 이 모양에 맞춰 읽는다.

이름을 바꾸거나 지우면 웹 클라이언트가 깨진다(없던 필드를 추가하는 것은 안전: 웹은 모르는 필드를 무시한다).
/docs(OpenAPI)에 응답 형식이 나오고, 서버가 내보내는 값도 이 형식에 맞는지 검증된다.
"""
from typing import Literal

from pydantic import BaseModel


class PredictModelInfo(BaseModel):
    name: str
    label: str
    version: str
    scope: str
    trainedEnd: str
    predictUntil: str
    championBasis: str | None = None
    partial: bool


class PredictionDay(BaseModel):
    date: str
    dayOfWeek: str
    predicted: int


class PredictResponse(BaseModel):
    routeNo: str
    model: PredictModelInfo
    predictions: list[PredictionDay]
    notice: str


class PredictorHealth(BaseModel):
    name: str
    version: str
    scope: str
    trainedEnd: str


class HealthResponse(BaseModel):
    """status: ok(예측 모델 사용 가능) / no_model(없음)."""
    status: Literal["ok", "no_model"]
    modelLoaded: bool
    model: PredictorHealth | None = None
    lastError: str | None = None
    warnings: list[str] = []
