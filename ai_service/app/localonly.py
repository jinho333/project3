"""로컬 전용 접근 제한 (개발용 API와 예측 API가 함께 쓴다).

실수로 --host 0.0.0.0 으로 띄워도 외부 요청을 막고, 브라우저를 통한 DNS 리바인딩(다른 도메인 이름을 127.0.0.1로 가리켜
개발자 PC의 이 API를 부르게 하는 공격)을 막기 위해 Host 헤더도 확인한다.
"""
from fastapi import HTTPException, Request

LOCAL_HOSTS = {"127.0.0.1", "::1", "localhost", "testclient"}  # testclient: FastAPI TestClient 기본값
LOCAL_HOST_HEADERS = {"127.0.0.1", "::1", "localhost", "testserver"}  # testserver: TestClient가 보내는 Host 기본값


def host_name(header: str) -> str:
    """Host 헤더에서 포트를 뺀 이름 (예: "localhost:8000" → "localhost", "[::1]:8000" → "::1")."""
    header = header.strip().lower()
    if header.startswith("["):
        return header[1:].split("]", 1)[0]
    return header.rsplit(":", 1)[0] if header.count(":") == 1 else header


def local_only(request: Request) -> None:
    host = request.client.host if request.client else None
    if host not in LOCAL_HOSTS:
        raise HTTPException(403, "이 API는 로컬에서만 사용할 수 있습니다.")
    header_host = host_name(request.headers.get("host") or "")
    if header_host not in LOCAL_HOST_HEADERS:
        raise HTTPException(403, "이 API는 로컬 주소(localhost, 127.0.0.1)로만 접근할 수 있습니다.")
