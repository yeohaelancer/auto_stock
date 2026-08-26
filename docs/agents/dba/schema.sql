-- =========================================================================
-- 주식 자동매매 시스템 DB 스키마 (MySQL 8.0+)
-- Stock auto-trading system database schema (MySQL 8.0+)
-- 작성: DBA Agent / 2026-08-24, MySQL 전환: 2026-08-25
-- 대상 DB: PC에 이미 설치된 MySQL의 JDWORKS 스키마, 테이블명은 trading_ 접두사로 다른 앱과 구분한다.
-- Target DB: the JDWORKS schema on the MySQL instance already installed on this PC;
-- tables are prefixed trading_ to stay distinct from other apps sharing the same schema.
--
-- ⚠️ MySQL은 파티션 테이블에 외래키(FK)를 허용하지 않는다 — trading_price_history/trading_feature_daily/
--    trading_prediction_log는 원래(PostgreSQL) 설계에서 월 단위 RANGE 파티셔닝 대상이었으나, FK 무결성을
--    유지하기 위해 일반 테이블 + 인덱스로 전환했다. 데이터가 급증하면 애플리케이션 레벨 아카이빙이나
--    FK를 포기한 파티셔닝을 별도로 검토할 것.
-- ⚠️ MySQL disallows foreign keys on partitioned tables — trading_price_history/trading_feature_daily/
--    trading_prediction_log were originally (PostgreSQL) monthly RANGE-partitioned, but are now plain
--    tables + indexes to keep FK integrity. Revisit with app-level archiving or FK-free partitioning
--    if data volume grows too large.
--
-- ⚠️ MySQL은 부분(필터) 유니크 인덱스를 지원하지 않는다 — 원래 `WHERE deleted_at IS NULL` 조건이 붙던
--    유니크 인덱스(uq_price_history 등)는 소프트 삭제된 행까지 포함해 전체에 적용된다. 해당 테이블들은
--    delete 후 재삽입이 아니라 upsert(ON DUPLICATE KEY UPDATE)로 갱신되므로 실질적 영향은 없다.
-- ⚠️ MySQL has no partial/filtered unique index — the unique indexes that previously carried a
--    `WHERE deleted_at IS NULL` clause (e.g. uq_price_history) now apply across all rows, including
--    soft-deleted ones. No practical impact here since these tables are updated via upsert
--    (ON DUPLICATE KEY UPDATE), not delete-then-reinsert.
-- =========================================================================

-- -------------------------------------------------------------------------
-- 1. trading_stock_master : 종목 기본정보
-- 1. trading_stock_master: basic stock master data
-- -------------------------------------------------------------------------
CREATE TABLE trading_stock_master (
    stock_code      VARCHAR(10)   PRIMARY KEY,               -- 종목코드 (Stock code, e.g. '005930')
    stock_name      VARCHAR(100)  NOT NULL,                  -- 종목명 (Stock name)
    market_type     VARCHAR(10)   NOT NULL,                  -- 시장구분: KOSPI/KOSDAQ (Market type)
    listed_date     DATE,                                    -- 상장일 (Listing date)
    is_managed      BOOLEAN       NOT NULL DEFAULT FALSE,     -- 관리종목 여부 (Is under management/watch)
    is_trading_halt BOOLEAN       NOT NULL DEFAULT FALSE,     -- 거래정지 여부 (Is trading halted)
    -- 거래대금 상위 자동선정 배치가 채운 행인지 구분 — 사용자가 수동으로 추가한 종목까지 자동 배치가
    -- 실수로 비활성화(soft delete)하지 않도록 하기 위함 (UniverseSelectionService).
    -- Distinguishes rows populated by the auto-selection batch, so it never soft-deletes stocks a
    -- human added manually (UniverseSelectionService).
    is_auto_selected BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,                       -- soft delete
    CONSTRAINT chk_market_type CHECK (market_type IN ('KOSPI', 'KOSDAQ'))
);
CREATE INDEX idx_stock_master_market_type ON trading_stock_master (market_type, deleted_at);
-- 유동성 낮은/관리종목 필터링(§5.3 종목 유니버스 필터링)에 사용
-- Used for liquidity/managed-stock universe filtering (design doc §5.3)
CREATE INDEX idx_stock_master_filter ON trading_stock_master (is_managed, is_trading_halt, deleted_at);

-- -------------------------------------------------------------------------
-- 2. trading_price_history : 일봉/분봉 시세 (OHLCV)
-- 2. trading_price_history: daily/minute OHLCV price data
-- -------------------------------------------------------------------------
CREATE TABLE trading_price_history (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    stock_code      VARCHAR(10)   NOT NULL REFERENCES trading_stock_master(stock_code),
    interval_type   VARCHAR(10)   NOT NULL,                  -- DAILY / MINUTE (봉 종류)
    trade_datetime  TIMESTAMP     NOT NULL,                  -- 시세 기준 일시 (Bar timestamp)
    open_price      NUMERIC(15,2) NOT NULL,
    high_price      NUMERIC(15,2) NOT NULL,
    low_price       NUMERIC(15,2) NOT NULL,
    close_price     NUMERIC(15,2) NOT NULL,
    volume          BIGINT        NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,
    CONSTRAINT chk_interval_type CHECK (interval_type IN ('DAILY', 'MINUTE')),
    CONSTRAINT fk_price_history_stock FOREIGN KEY (stock_code) REFERENCES trading_stock_master(stock_code)
);
CREATE INDEX idx_price_history_lookup ON trading_price_history (stock_code, interval_type, trade_datetime DESC);
-- 동일 종목·봉종류·시각 중복 저장 방지 + upsert(ON DUPLICATE KEY UPDATE) 지원 (시세 수집 배치용)
-- Prevents duplicate stock/interval/timestamp rows + supports upsert via ON DUPLICATE KEY UPDATE (for the price-collection batch)
CREATE UNIQUE INDEX uq_price_history ON trading_price_history (stock_code, interval_type, trade_datetime);

-- -------------------------------------------------------------------------
-- 3. trading_feature_daily : AI 모델 입력 피처 (기술적 지표)
-- 3. trading_feature_daily: technical indicator features for AI model input
-- -------------------------------------------------------------------------
CREATE TABLE trading_feature_daily (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    stock_code      VARCHAR(10)   NOT NULL REFERENCES trading_stock_master(stock_code),
    base_date       DATE          NOT NULL,                  -- 기준일 (Feature base date)
    ma5             NUMERIC(15,4),                            -- 5일 이동평균 (5-day moving average)
    ma20            NUMERIC(15,4),                            -- 20일 이동평균
    rsi14           NUMERIC(7,4),                              -- RSI(14)
    macd            NUMERIC(15,4),                             -- MACD
    bollinger_upper NUMERIC(15,2),
    bollinger_lower NUMERIC(15,2),
    feature_version VARCHAR(50)   NOT NULL,                   -- 피처 산출 로직 버전 (Feature pipeline version). 실운영 중 발견: 20자로는 "v1-standard-indicators"(22자)도 못 들어가 INSERT가 매번 실패했음 (found in real operation: 20 chars couldn't even fit "v1-standard-indicators" (22 chars), so every INSERT failed)
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,
    CONSTRAINT fk_feature_daily_stock FOREIGN KEY (stock_code) REFERENCES trading_stock_master(stock_code)
);
CREATE UNIQUE INDEX uq_feature_daily ON trading_feature_daily (stock_code, base_date, feature_version);

-- -------------------------------------------------------------------------
-- 4. trading_prediction_log : AI 예측 결과 이력
-- 4. trading_prediction_log: AI prediction result history
-- -------------------------------------------------------------------------
CREATE TABLE trading_prediction_log (
    id                BIGINT        AUTO_INCREMENT PRIMARY KEY,
    stock_code        VARCHAR(10)   NOT NULL REFERENCES trading_stock_master(stock_code),
    predict_date      DATE          NOT NULL,                -- 예측 실행일 (Prediction run date)
    predict_direction VARCHAR(10)   NOT NULL,                 -- UP/DOWN/FLAT (예측 방향)
    confidence        NUMERIC(5,4)  NOT NULL,                 -- 신뢰도 0~1 (Confidence score)
    expected_return    NUMERIC(9,6),                           -- 예상 수익률 (nullable, 회귀모델 사용 시)
    model_version      VARCHAR(30)   NOT NULL,                 -- 모델 버전 (Model version, e.g. 'lgbm-2026.08.1')
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP     NULL,
    CONSTRAINT chk_predict_direction CHECK (predict_direction IN ('UP', 'DOWN', 'FLAT')),
    CONSTRAINT chk_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT fk_prediction_log_stock FOREIGN KEY (stock_code) REFERENCES trading_stock_master(stock_code)
);
CREATE INDEX idx_prediction_log_lookup ON trading_prediction_log (stock_code, predict_date DESC);

-- -------------------------------------------------------------------------
-- 5. trading_strategy_signal : 매매 신호
-- 5. trading_strategy_signal: buy/sell/hold trading signals
-- -------------------------------------------------------------------------
CREATE TABLE trading_strategy_signal (
    signal_id       CHAR(36)      PRIMARY KEY,                -- 애플리케이션(Java UUID.randomUUID())이 생성해 전달 (App-generated via Java UUID.randomUUID())
    stock_code      VARCHAR(10)   NOT NULL REFERENCES trading_stock_master(stock_code),
    prediction_id   BIGINT,                                   -- 근거가 된 예측 로그 참조 (nullable, 소프트 삭제 대비)
    signal_type     VARCHAR(10)   NOT NULL,                    -- BUY/SELL/HOLD
    generated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED/EXPIRED
    reject_reason   VARCHAR(200),                              -- 리스크 엔진 기각 사유 (nullable)
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,
    CONSTRAINT chk_signal_type CHECK (signal_type IN ('BUY', 'SELL', 'HOLD')),
    CONSTRAINT chk_signal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT fk_strategy_signal_stock FOREIGN KEY (stock_code) REFERENCES trading_stock_master(stock_code)
);
CREATE INDEX idx_strategy_signal_stock ON trading_strategy_signal (stock_code, generated_at DESC);
CREATE INDEX idx_strategy_signal_status ON trading_strategy_signal (status, deleted_at);

-- -------------------------------------------------------------------------
-- 6. trading_risk_log : 리스크 이벤트 로그 (trading_order_log보다 먼저 정의 — FK 참조 때문)
-- 6. trading_risk_log: risk engine event log (defined before trading_order_log due to FK reference)
-- -------------------------------------------------------------------------
CREATE TABLE trading_risk_log (
    risk_log_id     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(30)   NOT NULL,                   -- DAILY_LOSS_LIMIT/POSITION_LIMIT/ANOMALY_DETECTED/MANUAL_KILL_SWITCH
    occurred_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    account_id      VARCHAR(30)   NOT NULL,
    trading_mode    VARCHAR(4)    NOT NULL,                    -- MOCK/LIVE
    action_taken    VARCHAR(200)  NOT NULL,                    -- 조치 내용 (e.g. '신규 주문 중단')
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,
    CONSTRAINT chk_risk_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE'))
);
CREATE INDEX idx_risk_log_account ON trading_risk_log (account_id, trading_mode, occurred_at DESC);

-- -------------------------------------------------------------------------
-- 7. trading_order_log : 주문 이력
-- 7. trading_order_log: order execution history
-- -------------------------------------------------------------------------
CREATE TABLE trading_order_log (
    order_id          CHAR(36)      PRIMARY KEY,                -- 애플리케이션(Java UUID.randomUUID())이 생성해 전달 (App-generated via Java UUID.randomUUID())
    signal_id         CHAR(36)      REFERENCES trading_strategy_signal(signal_id), -- 근거 신호 (nullable: 수동 주문 대비)
    stock_code        VARCHAR(10)   NOT NULL REFERENCES trading_stock_master(stock_code),
    trading_mode      VARCHAR(4)    NOT NULL,                   -- MOCK/LIVE — 모의/실전 데이터 혼동 방지 핵심 컬럼
    order_type        VARCHAR(10)   NOT NULL,                    -- BUY/SELL
    quantity          INTEGER       NOT NULL,
    order_price       NUMERIC(15,2) NOT NULL,
    executed_price     NUMERIC(15,2),                             -- 체결가 (nullable, 미체결 시)
    execution_status  VARCHAR(20)   NOT NULL DEFAULT 'PENDING',   -- PENDING/FILLED/PARTIAL/CANCELLED/REJECTED
    blocked_by_risk_id BIGINT       REFERENCES trading_risk_log(risk_log_id), -- 리스크 엔진이 차단한 경우 참조 (nullable)
    kiwoom_order_no    VARCHAR(30),                               -- 키움 API 주문번호 (nullable, 접수 후 채워짐)
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP     NULL,
    CONSTRAINT chk_order_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_order_quantity CHECK (quantity > 0),
    CONSTRAINT chk_execution_status CHECK (execution_status IN ('PENDING', 'FILLED', 'PARTIAL', 'CANCELLED', 'REJECTED')),
    CONSTRAINT fk_order_log_signal FOREIGN KEY (signal_id) REFERENCES trading_strategy_signal(signal_id),
    CONSTRAINT fk_order_log_stock FOREIGN KEY (stock_code) REFERENCES trading_stock_master(stock_code),
    CONSTRAINT fk_order_log_risk FOREIGN KEY (blocked_by_risk_id) REFERENCES trading_risk_log(risk_log_id)
);
CREATE INDEX idx_order_log_stock ON trading_order_log (stock_code, trading_mode, created_at DESC);
CREATE INDEX idx_order_log_status ON trading_order_log (execution_status, deleted_at);

-- -------------------------------------------------------------------------
-- 8. trading_position : 현재 포지션 (계좌 + 모드 + 종목 단위)
-- 8. trading_position: current holdings per account + mode + stock
-- -------------------------------------------------------------------------
CREATE TABLE trading_position (
    position_id     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    account_id      VARCHAR(30)   NOT NULL,
    trading_mode    VARCHAR(4)    NOT NULL,                    -- MOCK/LIVE
    stock_code      VARCHAR(10)   NOT NULL REFERENCES trading_stock_master(stock_code),
    quantity        INTEGER       NOT NULL DEFAULT 0,
    avg_price       NUMERIC(15,2) NOT NULL DEFAULT 0,
    stop_loss_price NUMERIC(15,2),                              -- 손절가 (nullable)
    take_profit_price NUMERIC(15,2),                            -- 익절가 (nullable)
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,
    CONSTRAINT chk_position_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE')),
    CONSTRAINT chk_position_quantity CHECK (quantity >= 0),
    CONSTRAINT fk_position_stock FOREIGN KEY (stock_code) REFERENCES trading_stock_master(stock_code)
);
CREATE UNIQUE INDEX uq_position_account_stock ON trading_position (account_id, trading_mode, stock_code);

-- -------------------------------------------------------------------------
-- 9. trading_account_snapshot : 일별 계좌 스냅샷
-- 9. trading_account_snapshot: daily account valuation snapshot
-- -------------------------------------------------------------------------
CREATE TABLE trading_account_snapshot (
    snapshot_id     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    account_id      VARCHAR(30)   NOT NULL,
    trading_mode    VARCHAR(4)    NOT NULL,                    -- MOCK/LIVE
    snapshot_date   DATE          NOT NULL,
    total_value     NUMERIC(18,2) NOT NULL,                    -- 평가금액 (Total valuation)
    cash_balance    NUMERIC(18,2) NOT NULL,                    -- 현금 잔고
    daily_pnl       NUMERIC(18,2) NOT NULL,                    -- 당일 손익
    daily_pnl_rate  NUMERIC(7,4)  NOT NULL,                    -- 당일 손익률
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP     NULL,
    CONSTRAINT chk_snapshot_trading_mode CHECK (trading_mode IN ('MOCK', 'LIVE'))
);
CREATE UNIQUE INDEX uq_account_snapshot ON trading_account_snapshot (account_id, trading_mode, snapshot_date);
