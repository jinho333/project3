# ai_service (예측 서버)

노선 하나의 **일별 이용자 수**를 예측해 주는 FastAPI 서버입니다. 관리자 지도 화면의 "예측 데이터" 아래 "일별 이용자 수 예측"이 이 서버를 부릅니다.

- 값은 국토교통부 노선별 이용량(**AI 합성데이터**)으로 학습한 통계적 경향이며 개별 값의 정밀 예측이 아닙니다.
- 모델을 만드는 수집·학습 코드는 이 폴더에 없습니다(데이터·AI 담당자의 로컬에서 관리). 여기에는 **만들어진 모델로 예측만** 하는 코드와 모델 파일(`models/ALL.joblib`, `ALL.json`)이 있습니다.
- `models/ALL.joblib`은 pickle이라 **불러오는 것이 곧 코드 실행**입니다. 이 저장소에 올라온 파일(`ALL.json`의 sha256과 같은 파일)만 쓰세요. 서버는 시작할 때 sha256이 다르면 불러오지 않습니다.
- 모델을 다시 저장하면 `candidates.py`, `features.py`, `artifact.py`가 만든 쪽(학습 코드)과 **같은 내용**이어야 합니다. 이 파일들을 고칠 때는 담당자에게 알려 주세요.

## 실행

Python 3.12 이상(모델은 3.14.7, scikit-learn 1.9.1, pandas 3.0.5로 만들었습니다. 버전이 다르면 `/health`의 `warnings`에 나옵니다).

```bash
cd ai_service
pip install -r requirements.txt
cp .env.example .env        # PREDICT_API_KEY 에 16자 이상 무작위 문자열을 넣는다
uvicorn app.predict_api:app --host 127.0.0.1 --port 8000
```

웹(Spring)의 `application.yaml`(git에 올리지 않는 로컬 파일)에 같은 주소와 키를 넣습니다.

```yaml
py:
  base:
    url: http://127.0.0.1:8000
  api:
    key: <.env 의 PREDICT_API_KEY 와 같은 값>
```

예측 서버가 꺼져 있거나 키가 없어도 웹의 다른 화면은 그대로 동작하고, 이용자 수 예측 영역에만 안내 문구가 나옵니다.

## API (웹이 부르는 내부용, 로컬 전용 + `X-API-Key`)

| 요청 | 설명 |
|---|---|
| `GET /predict?routeNo=134&from=2026-11-04&to=2026-11-17` | 날짜별 이용자 수. 생략하면 하루, 최대 31일, 학습 마지막 날 + 56일까지 |
| `GET /health` | 상태·불러온 모델(키 불필요, 로컬) |

오류: 401 키 불일치, 404 학습에 없던 노선, 422 입력·기간 오류, 503 모델 없음. 응답 형식은 `app/api_models.py`와 `/docs`(OpenAPI)에 있습니다.

## 시험

```bash
cd ai_service
python -m pytest -q tests
```
