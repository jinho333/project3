"""환경설정. 값은 ai_service/.env 또는 환경변수에서 읽는다 (API 키를 코드에 넣지 않음)."""
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")


@dataclass(frozen=True)
class Settings:
    # 웹(Spring)이 보내는 X-API-Key 값과 같아야 한다 (application.yaml 의 py.api.key)
    predict_api_key: str = os.getenv("PREDICT_API_KEY", "")
    # 저장된 모델 폴더·범위(ALL 또는 구 코드)
    model_dir: Path = Path(os.getenv("MODEL_DIR", "")) if os.getenv("MODEL_DIR") else Path(__file__).resolve().parent.parent / "models"
    model_scope: str = os.getenv("MODEL_SCOPE", "ALL")


settings = Settings()
