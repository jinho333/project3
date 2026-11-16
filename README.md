# 울산 버스 불편 신고 접수 · 처리 시스템

**신고는 쉽게, 처리는 급한 것부터.** 시민은 클릭 몇 번으로 노선별 불편을 신고하고, 관리자는 점수와 이유가 붙은 목록에서 급한 신고부터 처리합니다.
그린AI캠퍼스 3차 팀 프로젝트(COMMIT, 3인)이며, 저는 팀장이었습니다.

| 신고 접수 (유형 · 노선 · 예시 문장 · AI 다듬기) | 관리자 (AI 요약 · 긴급 순 정렬) |
|---|---|
| ![신고 접수](docs/demo-report.gif) | ![관리자](docs/demo-admin.gif) |

## 한눈에 보기

| | |
|---|---|
| 신고 유형 순위 | 상위 4개 유형이 전체의 **72.2%** (경상일보 2025.10.20 · 울산시 2025년 1~9월 자료, 기사 건수로 계산) |
| 반복 신고 | 같은 사람이 같은 유형을 같은 주에 여러 번 신고해도 **1건**으로 집계 |
| 처리 우선순위 | 점수 = 0.4×심각도 + 0.3×이용자 규모 + 0.2×방치 + 0.1×반복 |
| 긴급 규칙 | 사고 위험 유형이거나 **14일 이상 방치**된 신고는 점수와 무관하게 맨 위 |

## 구조

```mermaid
flowchart LR
  B["브라우저<br/>시민: 신고 작성 · 조회<br/>관리자: 신고 관리 · 이용수요"] --> S["Spring Boot<br/>Thymeleaf · 세션 로그인 · BCrypt"]
  S -->|MyBatis| D[("MariaDB<br/>회원 · 신고 · 노선")]
  S -->|RestClient| F["FastAPI 예측 서버<br/>이용자 수 예측 (ai_service)"]
  S -->|REST| G["Gemini API<br/>문장 다듬기 · 관리자 요약"]
```

## 제가 맡은 부분

- 로그인/회원가입(BCrypt), 신고 작성 · 조회 · 수정 · 취소
- **신고 접수 검증**: 화면과 같은 규칙을 서버에서 다시 검사, 유형 이름은 DB 값 사용, 본인 신고 + '접수' 상태일 때만 수정·취소. 파일을 먼저 저장하고 DB 단계가 실패하면 직접 되돌림(트랜잭션 도입 전 임시 방식)
- **메인 유형 순위**: 같은 사람의 같은 주 반복 신고는 1건
- **처리 우선순위**: 규칙을 검증해 3가지 결함을 수정(예측 없는 노선 0점 처리 → 중간값, 반복 신고 반영 불일치, 오래된 신고 굶김 → 14일 긴급 구간)
- **관리자 AI 비서**: 숫자·고정 문구는 서버(`RouteFactText`)가, 해석만 Gemini가 작성. 신고 글 속 지시문은 따르지 않고 이름·전화번호는 보내지 않음. 타임아웃(5초/30초), 세션별 호출 간격(429), 성공한 답만 캐시
- **관리자 접근 제어**: `AdminInterceptor`로 모아 화면은 리다이렉트, `/admin/api/**`는 403 JSON
- 노선 관리, 예측 노선 번호 동기화, 울산 BIS 노선 이름 가져오기
- **테스트 43개**(6개 파일, 전체 73개 중 git 기준 제 작성분이 절반 이상인 파일)

이용자 수 예측 모델(`ai_service`), 노선 지도, 이용수요 화면은 팀원이 맡았습니다.

## 핵심 코드

| 주제 | 위치 |
|---|---|
| 처리 우선순위 점수와 긴급 규칙 | [`ReportPriorityService.java`](src/main/java/com/commit/project3/service/ReportPriorityService.java#L18) |
| 신고 접수: 파일 → DB 순서와 수동 되돌림 | [`ReportService.java`](src/main/java/com/commit/project3/service/ReportService.java#L133) |
| 유형 순위 쿼리 (주간 반복 신고 1건 집계) | [`report.xml`](src/main/resources/mapper/report.xml#L18) |
| AI: 서버가 쓰는 사실 문장 | [`RouteFactText.java`](src/main/java/com/commit/project3/service/RouteFactText.java#L13) |
| AI: 프롬프트(지시문 무시 · 개인정보 제외) | [`AiPrompts.java`](src/main/java/com/commit/project3/service/AiPrompts.java#L8) |
| 관리자 접근 제어 | [`AdminInterceptor.java`](src/main/java/com/commit/project3/config/AdminInterceptor.java#L14) |
| 테스트 | [`src/test`](src/test/java/com/commit/project3) |

```sql
-- 같은 사람이 같은 유형을 같은 주(월~일)에 여러 번 신고하면 1건으로 센다
COUNT(DISTINCT CONCAT_WS('|', IFNULL(R.MEMBER_NO, CONCAT('n', R.REPORT_NO)), YEARWEEK(R.REG_DATE, 1))) AS cnt
```

## 기술 스택

Java 17 · Spring Boot · MyBatis · Thymeleaf · MariaDB · BCrypt · Gemini API · FastAPI · JUnit 5 · Mockito · Gradle

## 실행

1. MariaDB `project-3` 데이터베이스를 준비합니다. (스키마 SQL은 이 저장소에 없습니다.)
2. 예측 서버를 실행합니다: [`ai_service/README.md`](ai_service/README.md)
3. `application.yaml`(DB 접속 · 예측 서버 주소 · API 키)을 본인 환경에 맞게 채우고 실행합니다.

```bash
./gradlew bootRun
```

## 한계

- 우선순위 비중(40·30·20·10)은 **시작값**입니다. 실제 운영 데이터에서 관리자 판단과 비교해 조정해야 합니다.
- 예측 서버의 학습 데이터는 [`ai_service/README.md`](ai_service/README.md)에 적힌 대로 AI 합성데이터이며, 개별 값의 정밀 예측이 아닌 통계적 경향입니다. 우선순위의 "이용자 규모" 점수에 이 예측값이 쓰입니다.
- 신고 접수의 파일 저장과 DB 저장은 수동으로 되돌립니다. 트랜잭션으로 옮겨야 합니다.
- 모바일을 위한 반응형 화면은 아직 없습니다.

## 기록

- [프로젝트 작업 일지 (Notion)](https://fluffy-plane-19c.notion.site/3e3a969c48278070bbaaf5dee74b31b4)
