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


class DayRouteRiders(BaseModel):
    routeNo: str
    predicted: int                  # 그날 하루 예상 이용자 수(명)


class DayRankingResponse(BaseModel):
    """하루 날짜 하나에 대한 모든 노선의 예상 이용자 수, 많은 순(같으면 노선번호순). 웹이 노선 목록을 이용자 수 순으로 정렬할 때 쓴다."""
    date: str
    dayOfWeek: str
    modelVersion: str
    count: int
    routes: list[DayRouteRiders]
    notice: str


class HourPrediction(BaseModel):
    hour: int                       # 0~23 (해당 시각의 한 시간 동안)
    predicted: int                  # 예상 승차 인원(명)


class HourlyResponse(BaseModel):
    """시간대별 예상 승차 인원 = 그날 일별 예측값 x 시각 비율. 시각별 값은 반올림해서 합이 dailyPredicted 와 1~2명 다를 수 있다."""
    routeNo: str
    date: str
    dayOfWeek: str
    dailyPredicted: int
    hours: list[HourPrediction]
    shareBasis: Literal["route", "blend", "global"]   # 비율의 근거: 노선 자체 / 전체 평균을 섞음 / 전체 평균(이 노선 자료 없음)
    shareDayKind: int                                 # 비율에 쓴 날 유형(0 평일 1 토 2 일·공휴일 3 명절). 요청 날의 유형 자료가 없으면 가까운 유형
    limited: bool                                     # true 면 비율 근거가 약함: 다른 날 유형 비율로 대신했거나 그 유형의 날짜가 3일 미만
    provisional: bool                                 # true 면 임시 비율표(자료가 적음)
    model: PredictModelInfo
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
