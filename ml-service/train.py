"""
LightGBM 학습 스크립트 — 1차 MVP 모델 (설계 §4.3).
LightGBM training script — Phase-1 MVP model (design doc §4.3).

⚠️ 중요: 실제 시세 이력을 바탕으로 한 정답 라벨(과거 N일 후 실제 등락)이 아직 없어,
   현재는 배선(feature_daily 조회 → 추론 → 응답)이 올바르게 동작하는지 검증하기 위한
   합성(synthetic) 라벨로만 학습한다. modelVersion에 "synthetic"을 명시해 실거래 신호로
   오인되지 않도록 한다 (Review 필수 반영사항).
⚠️ IMPORTANT: There is no real historical-outcome label data yet (actual N-day-later price
   moves). This currently trains on synthetic labels purely to verify the
   feature_daily → inference → response wiring. modelVersion is tagged "synthetic" so it can
   never be mistaken for a production-ready signal (per Review's must-fix finding).

실제 운영 전 필수 작업 (required before any production use):
  1. price_history/feature_daily 실데이터 축적
  2. 과거 N일 후 실제 등락으로 정답 라벨 생성
  3. 이 스크립트의 generate_synthetic_dataset()을 실데이터 조회로 교체 후 재학습
"""

import numpy as np
import pandas as pd
import lightgbm as lgb
import joblib

FEATURE_COLUMNS = ["ma5", "ma20", "rsi14", "macd", "bollinger_upper", "bollinger_lower"]
DIRECTION_LABELS = {0: "DOWN", 1: "FLAT", 2: "UP"}
MODEL_VERSION = "lgbm-synthetic-0.1"
MODEL_PATH = "model.pkl"


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
    # 합성 라벨 산출식 — 실제 가격 움직임과 무관, 배선 검증 목적 (synthetic label formula — unrelated to real price action)
    score = (df["ma5"] - df["ma20"]) + (df["rsi14"] - 50) * 0.1
    df["label"] = pd.cut(score, bins=[-np.inf, -1, 1, np.inf], labels=[0, 1, 2]).astype(int)
    return df


def train() -> None:
    df = generate_synthetic_dataset()
    X, y = df[FEATURE_COLUMNS], df["label"]
    model = lgb.LGBMClassifier(n_estimators=100, max_depth=5, random_state=42)
    model.fit(X, y)
    joblib.dump({"model": model, "version": MODEL_VERSION, "features": FEATURE_COLUMNS}, MODEL_PATH)
    print(f"모델 저장 완료: {MODEL_PATH} (version={MODEL_VERSION}) — 합성 데이터 기반, 실거래 사용 금지")


if __name__ == "__main__":
    train()
