"""
AI 예측 서비스 (FastAPI) — Spring Boot 백엔드와 분리된 별도 프로세스로 운영 (설계 §4.4).
AI prediction service (FastAPI) — runs as a process separate from the Spring Boot backend (design doc §4.4).

BUG-003 수정: 학습된 LightGBM 모델(train.py 산출물)을 로딩하고, feature_daily 테이블의 실제 최신
피처를 조회해 추론한다. 모델이 없거나 해당 종목의 피처가 없으면 절대 임의 예측을 만들지 않고
신뢰도 0인 placeholder를 반환한다 (설계 §10 "임의 값으로 대체 금지" 원칙).

⚠️ 이 모델은 현재 합성(synthetic) 라벨로 학습되어 있다 (train.py 참고) — modelVersion에 "synthetic"이
   포함되어 있는 한 실거래 신호로 사용해서는 안 된다.

BUG-003 fix: loads the trained LightGBM model (train.py's output) and looks up the stock's latest
real features from feature_daily for inference. If no model or no feature row exists, this never
fabricates a prediction — it returns a placeholder with confidence 0 instead (design doc §10,
"never substitute a fabricated value").

⚠️ The model is currently trained on synthetic labels (see train.py) — as long as modelVersion
   contains "synthetic", it must never be used as a live trading signal.
"""

import logging
import os

import joblib
import pandas as pd
import psycopg2
from fastapi import FastAPI
from pydantic import BaseModel

logger = logging.getLogger("autotrading-ml-service")
app = FastAPI(title="autotrading-ml-service")

_MODEL_PATH = os.path.join(os.path.dirname(__file__), "model.pkl")
_model_bundle = joblib.load(_MODEL_PATH) if os.path.exists(_MODEL_PATH) else None
if _model_bundle is None:
    logger.warning("학습된 모델(model.pkl)이 없습니다 — train.py를 먼저 실행하세요. "
                    "No trained model found — run train.py first.")

DIRECTION_LABELS = {0: "DOWN", 1: "FLAT", 2: "UP"}
PLACEHOLDER_VERSION = "placeholder-0.0"


class PredictRequest(BaseModel):
    stockCode: str
    baseDate: str


class PredictResponse(BaseModel):
    stockCode: str
    direction: str  # UP / DOWN / FLAT
    confidence: float  # 0.0 ~ 1.0
    expectedReturn: float | None = None
    modelVersion: str


def _fetch_latest_features(stock_code: str) -> tuple | None:
    """feature_daily에서 해당 종목의 최신 피처 1건을 조회한다. 없으면 None (조회 실패도 None으로 처리, fail-safe)."""
    """Fetch the latest feature_daily row for the stock. Returns None if absent (also fail-safe on query errors)."""
    try:
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "localhost"),
            port=os.getenv("DB_PORT", "5432"),
            dbname=os.getenv("DB_NAME", "autotrading"),
            user=os.getenv("DB_USER", "autotrading"),
            password=os.getenv("DB_PASSWORD", ""),
            connect_timeout=3,
        )
    except Exception:
        logger.exception("feature_daily 조회용 DB 연결 실패 (failed to connect to DB for feature_daily lookup)")
        return None

    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT ma5, ma20, rsi14, macd, bollinger_upper, bollinger_lower
                FROM feature_daily
                WHERE stock_code = %s AND deleted_at IS NULL
                ORDER BY base_date DESC
                LIMIT 1
                """,
                (stock_code,),
            )
            return cur.fetchone()
    except Exception:
        logger.exception("feature_daily 조회 실패 (feature_daily query failed)")
        return None
    finally:
        conn.close()


@app.get("/health")
def health() -> dict:
    """헬스체크 엔드포인트 (DevOps 모니터링 연동용). Health check endpoint (for DevOps monitoring)."""
    return {"status": "ok", "modelLoaded": _model_bundle is not None}


@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    """
    지정 종목/기준일에 대한 예측을 반환한다.
    Return a prediction for the given stock code and base date.

    모델 미탑재 또는 피처 데이터 없음 → 절대 임의 예측을 만들지 않고 신뢰도 0 placeholder 반환.
    No model loaded or no feature data → never fabricate a prediction; return a confidence-0 placeholder.
    """
    if _model_bundle is None:
        return PredictResponse(stockCode=request.stockCode, direction="FLAT", confidence=0.0,
                                expectedReturn=None, modelVersion=PLACEHOLDER_VERSION)

    row = _fetch_latest_features(request.stockCode)
    if row is None or any(v is None for v in row):
        return PredictResponse(stockCode=request.stockCode, direction="FLAT", confidence=0.0,
                                expectedReturn=None, modelVersion="no-feature-data")

    features = pd.DataFrame([row], columns=_model_bundle["features"])
    proba = _model_bundle["model"].predict_proba(features)[0]
    direction_idx = int(proba.argmax())

    return PredictResponse(
        stockCode=request.stockCode,
        direction=DIRECTION_LABELS[direction_idx],
        confidence=round(float(proba[direction_idx]), 4),
        expectedReturn=None,
        modelVersion=_model_bundle["version"],
    )
