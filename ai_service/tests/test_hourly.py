"""시간대별 예측 시험: 비율표 계산(작은 가짜 표)과 /predict/hourly(저장소의 실제 모델·비율표).

실행: cd ai_service && python -m pytest -q tests
"""
import json
from datetime import timedelta
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import hourly, predict_api

KEY = "test-key-test-key-1234"
MODEL_DIR = Path(__file__).resolve().parent.parent / "models"


def _flat(hot: int | None = None) -> list[float]:
    """합이 1인 24개 비율. hot 을 주면 그 시각에 전부 몰린다."""
    if hot is None:
        return [1 / 24] * 24
    return [1.0 if h == hot else 0.0 for h in range(24)]


def _table(**over) -> dict:
    data = {"version": 1, "k": 100, "builtFrom": {"days": 3, "first": "2026-01-01", "last": "2026-01-03"},
            "dayKindDays": {"0": 3, "2": 2},
            "global": {"0": _flat(), "2": _flat(12)},
            "routes": {"A": {"0": {"n": 900, "share": _flat(8)}}, "B": {"0": {"n": 10, "share": _flat(8)}}}}
    data.update(over)
    return data


def test_route_share_blend_weight_follows_sample_size():
    t = hourly.HourlyShares(_table())
    big, basis, kind, limited = t.shares("A", 0)              # n=900, k=100 -> 노선 비율 0.9
    assert (basis, kind, limited) == ("route", 0, False)
    assert big[8] == pytest.approx(0.9 + 0.1 / 24)
    small, basis, _, _ = t.shares("B", 0)                     # n=10 -> 노선 비율 약 0.09, 나머지는 전체 평균
    assert basis == "blend"
    assert small[8] == pytest.approx(10 / 110 + (100 / 110) / 24)
    assert sum(small) == pytest.approx(1.0)


def test_unknown_route_uses_global_and_missing_kind_falls_back():
    t = hourly.HourlyShares(_table())
    assert t.shares("ZZ", 0)[1] == "global"
    shares, _, kind, limited = t.shares("A", 3)               # 명절 자료 없음 -> 일·공휴일(2) 전체 평균, 다른 유형이라 limited
    assert kind == 2 and shares == _flat(12) and limited
    _, _, kind, limited = t.shares("A", 1)                    # 토요일 자료 없음 -> 일·공휴일(2)
    assert kind == 2 and limited


def test_limited_when_kind_has_fewer_than_three_days():
    data = _table(dayKindDays={"0": 2, "2": 5})               # 평일 자료가 2일뿐
    assert hourly.HourlyShares(data).shares("A", 0)[3] is True
    data = _table(dayKindDays={"0": 3, "2": 5})               # 3일이면 충분
    assert hourly.HourlyShares(data).shares("A", 0)[3] is False
    data = _table(dayKindDays={})                              # 날짜 수를 모르면 근거가 약하다고 본다
    assert hourly.HourlyShares(data).shares("A", 0)[3] is True


def test_holiday_season_is_always_limited_even_with_enough_days():
    data = _table(dayKindDays={"0": 56, "2": 16, "3": 30},    # 명절 날짜가 많아도 검증 오차가 커서 항상 근거가 약함
                  **{"global": {"0": _flat(), "2": _flat(12), "3": _flat(7)}})
    assert hourly.HourlyShares(data).shares("A", 3)[2:] == (3, True)
    assert hourly.HourlyShares(data).shares("A", 0)[3] is False   # 평일은 영향 없음


@pytest.mark.parametrize("bad", [
    {"global": {"0": [0.5] * 24}},                            # 합이 1이 아님
    {"global": {"0": [1.0]}},                                 # 24개가 아님
    {"version": 2},
    {"k": 0},
])
def test_broken_table_is_rejected(bad):
    with pytest.raises(hourly.HourlyError):
        hourly.HourlyShares(_table(**bad))


def test_holder_keeps_previous_table_when_new_file_is_broken(tmp_path):
    path = tmp_path / "ALL_hourly.json"
    path.write_text(json.dumps(_table()), encoding="utf-8")
    holder = hourly.HourlyHolder(tmp_path, "ALL")
    first = holder.get()
    assert first is not None
    path.write_text("{ 깨진 파일", encoding="utf-8")
    assert holder.get() is first and holder.last_error is not None


def test_holder_without_file_returns_none(tmp_path):
    holder = hourly.HourlyHolder(tmp_path, "ALL")
    assert holder.get() is None and "없습니다" in holder.last_error


@pytest.fixture()
def client():
    app = predict_api.create_app(holder=predict_api.ModelHolder(MODEL_DIR, "ALL"), api_key=KEY)
    with TestClient(app, client=("127.0.0.1", 50000)) as c:
        yield c


def _first_route_and_day(client):
    from app import artifact
    bundle = artifact.load_bundle(MODEL_DIR, "ALL")
    return bundle.routes[0], bundle.trained_end + timedelta(days=1)


def test_hourly_returns_24_hours_matching_daily(client):
    route, day = _first_route_and_day(client)
    h = {"X-API-Key": KEY}
    body = client.get("/predict/hourly", params={"routeNo": route, "date": day.isoformat()}, headers=h).json()
    daily = client.get("/predict", params={"routeNo": route, "from": day.isoformat()}, headers=h).json()["predictions"][0]["predicted"]
    assert body["dailyPredicted"] == daily
    assert [x["hour"] for x in body["hours"]] == list(range(24))
    assert all(x["predicted"] >= 0 for x in body["hours"])
    assert abs(sum(x["predicted"] for x in body["hours"]) - daily) <= 12      # 시각별 반올림 오차
    assert body["shareBasis"] in ("route", "blend", "global") and "합성데이터" in body["notice"]
    assert isinstance(body["limited"], bool) and isinstance(body["provisional"], bool)


def test_hourly_errors(client):
    route, day = _first_route_and_day(client)
    h = {"X-API-Key": KEY}
    assert client.get("/predict/hourly", params={"routeNo": route, "date": day.isoformat()}).status_code == 401
    assert client.get("/predict/hourly", params={"routeNo": "99999", "date": day.isoformat()}, headers=h).status_code == 404
    assert client.get("/predict/hourly", params={"routeNo": route, "date": "2000-01-01"}, headers=h).status_code == 422
    assert client.get("/predict/hourly", params={"routeNo": "a b", "date": day.isoformat()}, headers=h).status_code == 422


def test_hourly_without_table_is_503_but_daily_still_works(tmp_path):
    app = predict_api.create_app(holder=predict_api.ModelHolder(MODEL_DIR, "ALL"), api_key=KEY, hourly=hourly.HourlyHolder(tmp_path, "ALL"))
    with TestClient(app, client=("127.0.0.1", 50000)) as c:
        route, day = _first_route_and_day(c)
        h = {"X-API-Key": KEY}
        assert c.get("/predict/hourly", params={"routeNo": route, "date": day.isoformat()}, headers=h).status_code == 503
        assert c.get("/predict", params={"routeNo": route, "from": day.isoformat()}, headers=h).status_code == 200
