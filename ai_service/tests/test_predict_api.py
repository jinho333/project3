"""예측 API 시험: 저장소에 들어 있는 실제 모델 파일(models/ALL.joblib)로 확인한다.

실행: cd ai_service && python -m pytest -q tests
"""
from datetime import date, timedelta
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import artifact, predict_api

KEY = "test-key-test-key-1234"
MODEL_DIR = Path(__file__).resolve().parent.parent / "models"


@pytest.fixture(scope="module")
def bundle():
    return artifact.load_bundle(MODEL_DIR, "ALL")


@pytest.fixture()
def client(bundle):
    holder = predict_api.ModelHolder(MODEL_DIR, "ALL")
    app = predict_api.create_app(holder=holder, api_key=KEY)
    # TestClient 는 클라이언트 주소가 testclient 라서 로컬 확인을 통과하도록 127.0.0.1 로 지정
    with TestClient(app, client=("127.0.0.1", 50000)) as c:
        yield c


def _day(bundle) -> str:
    return (bundle.trained_end + timedelta(days=1)).isoformat()


def test_saved_model_loads_and_knows_routes(bundle):
    assert len(bundle.routes) > 0
    assert bundle.scope == "ALL"


def test_predict_returns_days_with_model_info(client, bundle):
    route = bundle.routes[0]
    start = bundle.trained_end + timedelta(days=1)
    r = client.get("/predict", params={"routeNo": route, "from": start.isoformat(), "to": (start + timedelta(days=2)).isoformat()},
                   headers={"X-API-Key": KEY})
    assert r.status_code == 200
    body = r.json()
    assert body["routeNo"] == route
    assert len(body["predictions"]) == 3
    assert all(isinstance(p["predicted"], int) and p["predicted"] >= 0 for p in body["predictions"])
    assert body["model"]["predictUntil"] >= body["predictions"][-1]["date"]
    assert "합성데이터" in body["notice"]


def test_predict_day_ranks_all_routes_by_riders_and_needs_key(client, bundle):
    day = _day(bundle)
    assert client.get("/predict/day", params={"date": day}).status_code == 401
    r = client.get("/predict/day", params={"date": day}, headers={"X-API-Key": KEY})
    assert r.status_code == 200
    body = r.json()
    assert body["count"] == len(body["routes"]) == len(bundle.routes)
    assert {x["routeNo"] for x in body["routes"]} == set(bundle.routes)         # 모든 노선, 중복 없음
    keys = [(-x["predicted"], x["routeNo"]) for x in body["routes"]]
    assert keys == sorted(keys)                                                 # 많은 순, 같으면 노선번호순
    # 노선 하나를 따로 받은 값과 같다
    first = body["routes"][0]
    one = client.get("/predict", params={"routeNo": first["routeNo"], "from": day}, headers={"X-API-Key": KEY}).json()
    assert one["predictions"][0]["predicted"] == first["predicted"]
    assert "합성데이터" in body["notice"]


def test_predict_day_outside_range_is_422(client, bundle):
    too_late = (bundle.trained_end + timedelta(days=500)).isoformat()
    r = client.get("/predict/day", params={"date": too_late}, headers={"X-API-Key": KEY})
    assert r.status_code == 422


def test_api_key_is_required(client, bundle):
    params = {"routeNo": bundle.routes[0], "from": _day(bundle)}
    assert client.get("/predict", params=params).status_code == 401
    assert client.get("/predict", params=params, headers={"X-API-Key": "wrong-key-wrong-key-1"}).status_code == 401


def test_unknown_route_is_404_and_bad_input_is_422(client, bundle):
    h = {"X-API-Key": KEY}
    assert client.get("/predict", params={"routeNo": "99999", "from": _day(bundle)}, headers=h).status_code == 404
    assert client.get("/predict", params={"routeNo": "a b", "from": _day(bundle)}, headers=h).status_code == 422
    assert client.get("/predict", params={"routeNo": bundle.routes[0], "from": "2000-01-01"}, headers=h).status_code == 422


def test_range_over_31_days_is_rejected(client, bundle):
    start = bundle.trained_end + timedelta(days=1)
    r = client.get("/predict", params={"routeNo": bundle.routes[0], "from": start.isoformat(),
                                       "to": (start + timedelta(days=40)).isoformat()}, headers={"X-API-Key": KEY})
    assert r.status_code == 422


def test_clamp_keeps_the_valid_days_when_the_window_passes_the_last_predictable_day(client, bundle):
    h = {"X-API-Key": KEY}
    last = bundle.horizon_end()
    params = {"routeNo": bundle.routes[0], "from": (last - timedelta(days=2)).isoformat(), "to": (last + timedelta(days=10)).isoformat()}
    assert client.get("/predict", params=params, headers=h).status_code == 422                 # 기본은 예전처럼 거절
    r = client.get("/predict", params={**params, "clamp": "true"}, headers=h)
    assert r.status_code == 200
    assert [p["date"] for p in r.json()["predictions"]][-1] == last.isoformat()
    assert len(r.json()["predictions"]) == 3
    # 시작 날짜 자체가 범위 밖이면 clamp 해도 거절
    out = {**params, "from": (last + timedelta(days=1)).isoformat(), "clamp": "true"}
    assert client.get("/predict", params=out, headers=h).status_code == 422


def test_health_works_without_key(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"
    assert r.json()["modelLoaded"] is True


def test_horizon_warning_by_days_left():
    end = date(2026, 12, 3)
    assert predict_api.horizon_warning(end, date(2026, 11, 4)) is None                         # 29일 남음: 아직 조용
    assert "28일 남았습니다" in predict_api.horizon_warning(end, date(2026, 11, 5))            # 경계: 28일
    assert "오늘이 마지막 날" in predict_api.horizon_warning(end, end)
    assert "끝났습니다" in predict_api.horizon_warning(end, date(2026, 12, 4))


def test_health_adds_horizon_warning_only_when_close(client, bundle, monkeypatch):
    class FakeDate(date):
        today_value = bundle.horizon_end() - timedelta(days=3)

        @classmethod
        def today(cls):
            return cls.today_value

    monkeypatch.setattr(predict_api, "date", FakeDate)
    warnings = client.get("/health").json()["warnings"]
    assert any("3일 남았습니다" in w for w in warnings)
    FakeDate.today_value = bundle.horizon_end() - timedelta(days=100)
    assert not any("예측 가능 기간" in w for w in client.get("/health").json()["warnings"])


def test_no_model_gives_503_and_health_says_so(tmp_path):
    app = predict_api.create_app(holder=predict_api.ModelHolder(tmp_path, "ALL"), api_key=KEY)
    with TestClient(app, client=("127.0.0.1", 50000)) as c:
        assert c.get("/health").json()["status"] == "no_model"
        r = c.get("/predict", params={"routeNo": "106", "from": date.today().isoformat()}, headers={"X-API-Key": KEY})
        assert r.status_code == 503


def test_empty_or_short_key_refuses_to_start(tmp_path):
    for key in ("", "short"):
        app = predict_api.create_app(holder=predict_api.ModelHolder(tmp_path, "ALL"), api_key=key)
        with pytest.raises(RuntimeError):
            with TestClient(app, client=("127.0.0.1", 50000)):
                pass


def test_non_local_clients_are_refused(bundle):
    app = predict_api.create_app(holder=predict_api.ModelHolder(MODEL_DIR, "ALL"), api_key=KEY)
    with TestClient(app, client=("8.8.8.8", 50000)) as c:
        assert c.get("/health").status_code in (403, 400)
