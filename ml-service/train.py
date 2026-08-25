"""
LightGBM 학습 스크립트 — 1차 MVP 모델 (설계 §4.3).
LightGBM training script — Phase-1 MVP model (design doc §4.3).

BUG(AI 모델 합성 데이터) 수정: feature_daily + price_history 실데이터로 학습하도록 전환했다.
정답 라벨은 "기준일로부터 PREDICTION_HORIZON_DAYS 거래일 후 실제 등락"으로 산출한다(진짜 미래 결과 기반).
Fixed the AI-model-runs-on-synthetic-data issue: now trains on real feature_daily + price_history
data. Labels are the *actual* future outcome — the real price move PREDICTION_HORIZON_DAYS trading
days after the base date.

실데이터가 부족하면(운영 초기 등) 합성 데이터로 폴백하되, modelVersion에 "synthetic"을 명시해
실거래 신호로 오인되지 않도록 한다 (Review 원칙 유지).
Falls back to synthetic data when real data is insufficient (e.g. early in operation), but tags
modelVersion with "synthetic" so it can never be mistaken for a production-ready signal
(keeps the principle Review flagged earlier).
"""

import os

import numpy as np
import pandas as pd
import psycopg2
import lightgbm as lgb
import joblib

FEATURE_COLUMNS = ["ma5", "ma20", "rsi14", "macd", "bollinger_upper", "bollinger_lower"]
DIRECTION_LABELS = {0: "DOWN", 1: "FLAT", 2: "UP"}
MODEL_PATH = "model.pkl"

# N거래일 후 등락으로 라벨을 만든다 (설계 §4.1 "N일 후 예측"). 배포 후 데이터가 쌓이며 조정 가능.
# Labels use the outcome N trading days later (design doc §4.1 "predict N days ahead"). Tune once more data accumulates.
PREDICTION_HORIZON_DAYS = 5
# 이 비율 이상 오르면 UP, 이하로 내리면 DOWN, 나머지는 FLAT — 거래비용을 감안한 최소 유의미 변동폭.
# Move >= this rate → UP, <= -this rate → DOWN, else FLAT — a minimum move large enough to matter after costs.
DIRECTION_THRESHOLD = 0.01
# 실데이터가 이 건수 미만이면 학습을 신뢰할 수 없다고 보고 합성 데이터로 폴백한다.
# Below this many real rows, training isn't considered trustworthy — falls back to synthetic data.
MIN_REAL_SAMPLES = 200


def _db_connection():
    return psycopg2.connect(
        host=os.getenv("DB_HOST", "localhost"),
        port=os.getenv("DB_PORT", "5432"),
        dbname=os.getenv("DB_NAME", "autotrading"),
        user=os.getenv("DB_USER", "autotrading"),
        password=os.getenv("DB_PASSWORD", ""),
        connect_timeout=5,
    )


def load_real_dataset() -> pd.DataFrame:
    """
    feature_daily의 각 (종목, 기준일)에 대해, 기준일 종가와 N거래일 후 종가를 조인해 실제 등락 라벨을 만든다.
    Joins each feature_daily (stock, base_date) row with the close price N trading days later to build
    a real outcome label.
    """
    query = """
        WITH ranked_price AS (
            SELECT stock_code,
                   trade_datetime::date AS trade_date,
                   close_price,
                   ROW_NUMBER() OVER (PARTITION BY stock_code ORDER BY trade_datetime) AS rn
            FROM price_history
            WHERE interval_type = 'DAILY' AND deleted_at IS NULL
        )
        SELECT f.stock_code, f.base_date, f.ma5, f.ma20, f.rsi14, f.macd,
               f.bollinger_upper, f.bollinger_lower,
               base.close_price AS base_close, future.close_price AS future_close
        FROM feature_daily f
        JOIN ranked_price base ON base.stock_code = f.stock_code AND base.trade_date = f.base_date
        JOIN ranked_price future ON future.stock_code = f.stock_code AND future.rn = base.rn + %(horizon)s
        WHERE f.deleted_at IS NULL
    """
    with _db_connection() as conn:
        df = pd.read_sql(query, conn, params={"horizon": PREDICTION_HORIZON_DAYS})

    df = df.dropna(subset=FEATURE_COLUMNS + ["base_close", "future_close"])
    if df.empty:
        return df

    return_rate = (df["future_close"].astype(float) - df["base_close"].astype(float)) / df["base_close"].astype(float)
    df["label"] = np.select(
        [return_rate >= DIRECTION_THRESHOLD, return_rate <= -DIRECTION_THRESHOLD],
        [2, 0],  # UP, DOWN
        default=1,  # FLAT
    )
    return df


def generate_synthetic_dataset(n: int = 2000, seed: int = 42) -> pd.DataFrame:
    """배선 검증용 합성 데이터셋. 실제 시장 데이터가 아니다 (synthetic dataset for wiring checks only — not real market data)."""
    rng = np.random.default_rng(seed)
    df = pd.DataFrame({
        "ma5": rng.normal(100, 10, n),
        "ma20": rng.normal(100, 10, n),
        "rsi14": rng.uniform(0, 100, n),
        "macd": rng.normal(0, 2, n),
        "bollinger_upper": rng.normal(110, 10, n),
        "bollinger_lower": rng.normal(90, 10, n),
    })
    score = (df["ma5"] - df["ma20"]) + (df["rsi14"] - 50) * 0.1
    df["label"] = pd.cut(score, bins=[-np.inf, -1, 1, np.inf], labels=[0, 1, 2]).astype(int)
    return df


def train() -> None:
    try:
        real_df = load_real_dataset()
    except Exception as e:
        print(f"실데이터 조회 실패, 합성 데이터로 폴백합니다: {e} (real-data query failed, falling back to synthetic: {e})")
        real_df = pd.DataFrame()

    if len(real_df) >= MIN_REAL_SAMPLES:
        df = real_df
        model_version = f"lgbm-real-h{PREDICTION_HORIZON_DAYS}d-n{len(df)}"
        print(f"실데이터 {len(df)}건으로 학습합니다 (training on {len(df)} real samples)")
    else:
        df = generate_synthetic_dataset()
        model_version = "lgbm-synthetic-0.1"
        print(f"실데이터 부족({len(real_df)}건 < 최소 {MIN_REAL_SAMPLES}건) — 합성 데이터로 학습합니다 "
              f"(insufficient real data ({len(real_df)} < {MIN_REAL_SAMPLES}) — training on synthetic data)")

    X, y = df[FEATURE_COLUMNS], df["label"]
    model = lgb.LGBMClassifier(n_estimators=100, max_depth=5, random_state=42)
    model.fit(X, y)
    joblib.dump({"model": model, "version": model_version, "features": FEATURE_COLUMNS}, MODEL_PATH)
    print(f"모델 저장 완료: {MODEL_PATH} (version={model_version})")


if __name__ == "__main__":
    train()
