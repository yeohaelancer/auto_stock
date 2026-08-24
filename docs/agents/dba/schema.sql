-- =========================================================================
-- 주식 자동매매 시스템 DB 스키마 (PostgreSQL)
-- Stock auto-trading system database schema (PostgreSQL)
-- 작성: DBA Agent / 2026-08-24
-- =========================================================================

-- 공통 확장: UUID 생성용 (필요 시 pgvector는 2단계에서 별도 추가)
-- Common extension for UUID generation (pgvector to be added in phase 2 if needed)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -------------------------------------------------------------------------
-- 1. stock_master : 종목 기본정보
-- 1. stock_master: basic stock master data
-- -------------------------------------------------------------------------
CREATE TABLE stock_master (
    stock_code      VARCHAR(10)   PRIMARY KEY,               -- 종목코드 (Stock code, e.g. '005930')
    stock_name      VARCHAR(100)  NOT NULL,                  -- 종목명 (Stock name)
    market_type     VARCHAR(10)   NOT NULL,                  -- 시장구분: KOSPI/KOSDAQ (Market type)
    listed_date     DATE,                                    -- 상장일 (Listing date)
    is_managed      BOOLEAN       NOT NULL DEFAULT FALSE,     -- 관리종목 여부 (Is under management/watch)
    is_trading_halt BOOLEAN       NOT NULL DEFAULT FALSE,     -- 거래정지 여부 (Is trading halted)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,                              -- soft delete
    CONSTRAINT chk_market_type CHECK (market_type IN ('KOSPI', 'KOSDAQ'))
);
CREATE INDEX idx_stock_master_market_type ON stock_master (market_type) WHERE deleted_at IS NULL;
-- 유동성 낮은/관리종목 필터링(§5.3 종목 유니버스 필터링)에 사용
-- Used for liquidity/managed-stock universe filtering (design doc §5.3)
CREATE INDEX idx_stock_master_filter ON stock_master (is_managed, is_trading_halt) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 2. price_history : 일봉/분봉 시세 (OHLCV) — 월 단위 파티셔닝
-- 2. price_history: daily/minute OHLCV price data — monthly partitioned
-- -------------------------------------------------------------------------
CREATE TABLE price_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    stock_code      VARCHAR(10)   NOT NULL REFERENCES stock_master(stock_code),
    interval_type   VARCHAR(10)   NOT NULL,                  -- DAILY / MINUTE (봉 종류)
    trade_datetime  TIMESTAMPTZ   NOT NULL,                  -- 시세 기준 일시 (Bar timestamp)
    open_price      NUMERIC(15,2) NOT NULL,
    high_price      NUMERIC(15,2) NOT NULL,
    low_price       NUMERIC(15,2) NOT NULL,
    close_price     NUMERIC(15,2) NOT NULL,
    volume          BIGINT        NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_interval_type CHECK (interval_type IN ('DAILY', 'MINUTE')),
    PRIMARY KEY (id, trade_datetime)
) PARTITION BY RANGE (trade_datetime);

CREATE INDEX idx_price_history_lookup ON price_history (stock_code, interval_type, trade_datetime DESC);

-- -------------------------------------------------------------------------
-- 3. feature_daily : AI 모델 입력 피처 (기술적 지표) — 월 단위 파티셔닝
-- 3. feature_daily: technical indicator features for AI model input — monthly partitioned
-- -------------------------------------------------------------------------
CREATE TABLE feature_daily (
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    stock_code      VARCHAR(10)   NOT NULL REFERENCES stock_master(stock_code),
    base_date       DATE          NOT NULL,                  -- 기준일 (Feature base date)
    ma5             NUMERIC(15,4),                            -- 5일 이동평균 (5-day moving average)
    ma20            NUMERIC(15,4),                            -- 20일 이동평균
    rsi14           NUMERIC(7,4),                              -- RSI(14)
    macd            NUMERIC(15,4),                             -- MACD
    bollinger_upper NUMERIC(15,2),
    bollinger_lower NUMERIC(15,2),
    feature_version VARCHAR(20)   NOT NULL,                   -- 피처 산출 로직 버전 (Feature pipeline version)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    PRIMARY KEY (id, base_date)
) PARTITION BY RANGE (base_date);

CREATE UNIQUE INDEX uq_feature_daily ON feature_daily (stock_code, base_date, feature_version);

-- -------------------------------------------------------------------------
-- 4. prediction_log : AI 예측 결과 이력 — 월 단위 파티셔닝
-- 4. prediction_log: AI prediction result history — monthly partitioned
-- -------------------------------------------------------------------------
CREATE TABLE prediction_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    stock_code        VARCHAR(10)   NOT NULL REFERENCES stock_master(stock_code),
    predict_date      DATE          NOT NULL,                -- 예측 실행일 (Prediction run date)
    predict_direction VARCHAR(10)   NOT NULL,                 -- UP/DOWN/FLAT (예측 방향)
    confidence        NUMERIC(5,4)  NOT NULL,                 -- 신뢰도 0~1 (Confidence score)
    expected_return    NUMERIC(9,6),                           -- 예상 수익률 (nullable, 회귀모델 사용 시)
    model_version      VARCHAR(30)   NOT NULL,                 -- 모델 버전 (Model version, e.g. 'lgbm-2026.08.1')
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT chk_predict_direction CHECK (predict_direction IN ('UP', 'DOWN', 'FLAT')),
    CONSTRAINT chk_confidence CHECK (confidence BETWEEN 0 AND 1),
    PRIMARY KEY (id, predict_date)
) PARTITION BY RANGE (predict_date);

CREATE INDEX idx_prediction_log_lookup ON prediction_log (stock_code, predict_date DESC);

-- -------------------------------------------------------------------------
-- 5. strategy_signal : 매매 신호
-- 5. strategy_signal: buy/sell/hold trading signals
-- -------------------------------------------------------------------------
CREATE TABLE strategy_signal (
    signal_id       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_code      VARCHAR(10)   NOT NULL REFERENCES stock_master(stock_code),
    prediction_id   BIGINT,                                   -- 근거가 된 예측 로그 참조 (nullable, 소프트 삭제 대비)
    signal_type     VARCHAR(10)   NOT NULL,                    -- BUY/SELL/HOLD
    generated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED/EXPIRED
    reject_reason   VARCHAR(200),                              -- 리스크 엔진 기각 사유 (nullable)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_signal_type CHECK (signal_type IN ('BUY', 'SELL', 'HOLD')),
    CONSTRAINT chk_signal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'))
);
CREATE INDEX idx_strategy_signal_stock ON strategy_signal (stock_code, generated_at DESC);
CREATE INDEX idx_strategy_signal_status ON strategy_signal (status) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 6. risk_log : 리스크 이벤트 로그 (order_log보다 먼저 정의 — FK 참조 때문)
-- 6. risk_log: risk engine event log (defined before order_log due to FK reference)
-- -------------------------------------------------------------------------
CREATE TABLE risk_log (
    risk_log_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type      VARCHAR(30)   NOT NULL,                   -- DAILY_LOSS_LIMIT/POSITION_LIMIT/ANOMALY_DETECTED/MANUAL_KILL_SWITCH
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    account_id      VARCHAR(30)   NOT NULL,
    trading_mode    VARCHAR(4)    NOT NULL,                    -- MOCK/LIVE
    action_taken    VARCHAR(200)  NOT NULL,                    -- 조치 내용 (e.g. '신규 주문 중단')
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_risk_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE'))
);
CREATE INDEX idx_risk_log_account ON risk_log (account_id, trading_mode, occurred_at DESC);

-- -------------------------------------------------------------------------
-- 7. order_log : 주문 이력
-- 7. order_log: order execution history
-- -------------------------------------------------------------------------
CREATE TABLE order_log (
    order_id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id         UUID          REFERENCES strategy_signal(signal_id), -- 근거 신호 (nullable: 수동 주문 대비)
    stock_code        VARCHAR(10)   NOT NULL REFERENCES stock_master(stock_code),
    trading_mode      VARCHAR(4)    NOT NULL,                   -- MOCK/LIVE — 모의/실전 데이터 혼동 방지 핵심 컬럼
    order_type        VARCHAR(10)   NOT NULL,                    -- BUY/SELL
    quantity          INTEGER       NOT NULL,
    order_price       NUMERIC(15,2) NOT NULL,
    executed_price     NUMERIC(15,2),                             -- 체결가 (nullable, 미체결 시)
    execution_status  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING/FILLED/PARTIAL/CANCELLED/REJECTED
    blocked_by_risk_id BIGINT       REFERENCES risk_log(risk_log_id), -- 리스크 엔진이 차단한 경우 참조 (nullable)
    kiwoom_order_no    VARCHAR(30),                               -- 키움 API 주문번호 (nullable, 접수 후 채워짐)
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    CONSTRAINT chk_order_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_order_quantity CHECK (quantity > 0),
    CONSTRAINT chk_execution_status CHECK (execution_status IN ('PENDING', 'FILLED', 'PARTIAL', 'CANCELLED', 'REJECTED'))
);
CREATE INDEX idx_order_log_stock ON order_log (stock_code, trading_mode, created_at DESC);
CREATE INDEX idx_order_log_status ON order_log (execution_status) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 8. position : 현재 포지션 (계좌 + 모드 + 종목 단위)
-- 8. position: current holdings per account + mode + stock
-- -------------------------------------------------------------------------
CREATE TABLE position (
    position_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(30)   NOT NULL,
    trading_mode    VARCHAR(4)    NOT NULL,                    -- MOCK/LIVE
    stock_code      VARCHAR(10)   NOT NULL REFERENCES stock_master(stock_code),
    quantity        INTEGER       NOT NULL DEFAULT 0,
    avg_price       NUMERIC(15,2) NOT NULL DEFAULT 0,
    stop_loss_price NUMERIC(15,2),                              -- 손절가 (nullable)
    take_profit_price NUMERIC(15,2),                            -- 익절가 (nullable)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_position_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE')),
    CONSTRAINT chk_position_quantity CHECK (quantity >= 0)
);
CREATE UNIQUE INDEX uq_position_account_stock ON position (account_id, trading_mode, stock_code) WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 9. account_snapshot : 일별 계좌 스냅샷
-- 9. account_snapshot: daily account valuation snapshot
-- -------------------------------------------------------------------------
CREATE TABLE account_snapshot (
    snapshot_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(30)   NOT NULL,
    trading_mode    VARCHAR(4)    NOT NULL,                    -- MOCK/LIVE
    snapshot_date   DATE          NOT NULL,
    total_value     NUMERIC(18,2) NOT NULL,                    -- 평가금액 (Total valuation)
    cash_balance    NUMERIC(18,2) NOT NULL,                    -- 현금 잔고
    daily_pnl       NUMERIC(18,2) NOT NULL,                    -- 당일 손익
    daily_pnl_rate  NUMERIC(7,4)  NOT NULL,                    -- 당일 손익률
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_snapshot_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE'))
);
CREATE UNIQUE INDEX uq_account_snapshot ON account_snapshot (account_id, trading_mode, snapshot_date) WHERE deleted_at IS NULL;

-- =========================================================================
-- 파티션 생성 예시 (월 단위) — 운영 시 스케줄러/마이그레이션 도구로 자동화 필요
-- Example monthly partition creation — should be automated via scheduler/migration tool in production
-- =========================================================================
CREATE TABLE price_history_2026_08 PARTITION OF price_history
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE feature_daily_2026_08 PARTITION OF feature_daily
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE prediction_log_2026_08 PARTITION OF prediction_log
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
