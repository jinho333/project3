# 버스 이용수요(구 버스 혼잡도) 페이지 작업 인수인계

브랜치: `ft/congestion-page` (기준: `dev` 297e1cc, PR #15 `ft/admin-design` 병합 이후)
작성일: 2026-11-06

## 한 줄 요약
혼잡 등급 표시를 걷어내고 "미처리 신고 + 이용자 수 예측" 중심 노선 페이지로 바꾸는 중. AI 분석은 실제 이용자 수 예측 기준으로 교체 완료, 화면은 상위 5개 / 접기 / 노선 비교까지 구현. 직접 실행해서 눈으로 확인하는 일이 남음.

## 팀 합의 방향 (2026-11-06 보현 결정)
- 노선마다 대표 1방향만 지도에 표시 (방향 토글/칩 없음). 신고와 예측 수치는 노선번호 단위(양방향 합계).
- 대시보드 우선순위에서 노선 클릭 → 이 페이지가 그 노선이 선택된 상태로 열림. 직접 들어오면 상위 5개 노선.
- 비교는 선택 노선 + 비교 노선 1개, 총 2개까지.
- 미처리 신고가 없는 노선은 "미처리 없는 노선 N개"로 접음 (검색하면 전체 검색).
- AI는 노선 1개(이용 예측 + 미처리 신고) 또는 2개(차이 설명)에 답변.
- **페이지 이름은 미정.** 지금 메뉴 문구는 "버스 이용수요". 후보: 노선 분석(추천) / 버스 이용수요 / 노선 상세. "버스 노선정보"는 "노선 관리"와 겹쳐서 비추천.

## 이번 브랜치에서 바뀐 것
| 영역 | 내용 |
|---|---|
| 이름 | 상단 메뉴 "버스 혼잡도" → "버스 이용수요" (URL `/admin/congestion`, 파일명은 그대로) |
| 혼잡 삭제 | 목록의 혼잡/보통/여유 범례·순위·등급, 지도 툴팁의 혼잡도·순위, `route-list.json`의 등급 필드, `--grade-*` 색 토큰 삭제 |
| 지도 선 색 | 등급 색 대신 노선마다 다른 색 (`dashboard_map.js`의 `ROUTE_COLORS`). 목록 점·툴팁·지도 선이 같은 색 |
| 임시 차트 삭제 | 예측 데이터의 "1시간 뒤 혼잡 예측" 차트·요약과 `/admin/api/routes/{노선}/forecast` 삭제 |
| 가짜 코드 삭제 | `RouteForecastService`(가짜 혼잡도) 삭제, 시험용 `?hour=` 삭제 |
| AI | `AiService`가 `RidershipForecastService`(예측 서버)를 읽음. 노선 1개 4줄 / 노선 2개 비교 4줄. 숫자 문장은 서버가 쓰고 AI는 설명만. `POST /admin/api/ai/route` 에 `compareRouteNo` 추가 |
| 우선순위 | `GET /admin/api/routes/priority` 신설 (미처리 있는 노선만, 건수 많은 순 → 오래된 신고 순). **임시 기준** — 대시보드 우선순위가 정해지면 `AdminService.getRoutePriority()`만 바꾸면 양쪽이 같은 순서 |
| 화면 | 처음 화면 = 상위 5개 노선, 목록 = 우선순위 순서 + 접기, 노선 선택 시 다른 노선에 "비교" 체크, 이용자 차트 2개 노선(같은 눈금 / 규모 차이 3배 이상이면 안내 / "자기 최대값 기준" 선택), 오른쪽 패널에 비교 노선 신고 수 한 줄, AI 버튼이 비교 노선을 함께 보냄 |
| 자동 선택 | `/admin/congestion?route=134` 로 들어오면 134가 선택된 채로 시작 (대시보드 연결용, 대시보드 쪽 링크는 아직 안 만듦) |
| 테스트 | `AiServiceRouteTest` 8개 추가. 전체 `./gradlew test --offline` 통과 |

## 확인한 것 / 못 한 것
- 확인: Java 컴파일, 전체 테스트, 맵 스크립트를 가짜 DOM·가짜 카카오맵으로 돌린 스모크 테스트(우선순위 순서, 접기, 선택, 비교, 해제, `?route=`).
- **못 한 것: 실제 브라우저 화면 확인.** 지도 선 색, 비교 체크 위치, 접는 버튼 모양은 직접 실행해서 봐야 함.
- **못 한 것: 실제 Gemini 호출.** AI 답변 문장 품질은 "AI 분석"을 눌러 봐야 함.
- 노선 2개 분석은 화면에서 비교 체크 후 "AI 분석" 버튼으로 호출됨 (서버는 테스트로 확인).

## 남은 일
1. 실행해서 화면 확인 (지도 선 색 마음에 드는지, 목록·접기·비교 체크 배치).
2. 대시보드: 우선순위 데이터 + 노선 클릭 시 `/admin/congestion?route=번호` 로 이동하는 링크.
3. 우선순위 기준 확정 (지금은 임시: 미처리 건수 → 가장 오래된 신고 일수).
4. 페이지 이름 확정 후 `PRODUCT.md`, `DESIGN.md`, CSS 주석의 "버스 혼잡도" 문구 정리.
5. 시간대별 예상 승차 인원 차트는 아직 선택한 노선 1개만 (비교 노선 미반영).
6. 노선 정보 DB화: 노선 목록이 아직 `route-list.json` 10개 + geojson 11개. 예측 서버 쪽 DB(`bus_route`, `route_stop`, `bus_timetable`, 출처 울산 BIS)를 재사용할 계획이라, 팀원에게 확인 필요:
   - 그 표들은 어느 DB에 있는가 (우리 Spring과 같은 DB인가)
   - 수집기(`app.collect`)는 누구 로컬/어느 저장소에 있고 다시 실행 가능한가
   - 정류장 좌표(위도·경도)가 들어 있는가
   - 주의: Spring의 `BUS_ROUTE`(ROUTE_NO, ROUTE_NAME)와 예측 쪽 `bus_route`(route_id, route_no, route_class, direction)는 이름이 같아서 같은 DB면 충돌 가능
7. (선택) 노선별 혼잡도 OpenAPI는 합성데이터라 혼잡 등급을 만들기엔 부족하다고 결론. 상세는 메모리 `ulsan-congestion-api-findings`.

## 집에서 실행하려면 (git에 없는 것)
키와 DB 정보는 git에 올라가지 않으므로 집 PC에 직접 있어야 함.
- `src/main/resources/application.yaml` — DB 접속, `kakao.js.key`, `kakao.rest.key`, `gemini.api.key`, `py.base.url`, `py.api.key`
- `ai_service/.env` — `PREDICT_API_KEY` (= yaml의 `py.api.key`, 16자 이상 같은 값)
- 예측 서버: `ai_service`에서 가상환경 켜고 `uvicorn app.predict_api:app --host 127.0.0.1 --port 8000` (정확한 명령은 `ai_service/README.md`). 서버가 꺼져 있으면 이용자 차트에 안내 문구가 나오고 AI는 신고 분석만 함.
- Spring 실행: IDE에서 실행하거나 `./gradlew bootRun`, 주소 `http://localhost:8080/admin/congestion` (관리자 로그인 필요)

## 집에서 git 순서
```
git fetch origin
git checkout ft/congestion-page
git pull
```
작업 후:
```
git status
git add <바꾼 파일들>
git commit -m "메시지"
git push origin ft/congestion-page
```
PR: https://github.com/jinho333/project3/pull/new/ft/congestion-page (base는 `dev`)

## 참고 문서
- 관리자 디자인 규칙: `DESIGN.md`, `PRODUCT.md`
- 예측 서버: `ai_service/README.md`
