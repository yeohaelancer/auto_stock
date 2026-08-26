-- 배포 첫날 초기 계좌 스냅샷 시딩 스크립트.
-- Initial account snapshot seeding script for deployment day 1.
--
-- TradingScheduler.intradaySignalScan()은 "전일 계좌 스냅샷"이 있어야 실행된다. 그 스냅샷은
-- 원래 postMarketJob(15:40)이 매일 만드는데, 배포 첫날은 그게 아직 없어서 장중 스캔이 하루 종일
-- 스킵된다 — 버그가 아니라 "임의 값으로 대체하지 않는다"는 설계 원칙(§10)에 따른 의도된 안전장치다.
-- 이 스크립트는 그 첫날 공백을 메우기 위해 초기 스냅샷 1건을 수동으로 심는다.
--
-- TradingScheduler.intradaySignalScan() only runs when a "previous day's account snapshot" exists.
-- That snapshot is normally created daily by postMarketJob (15:40), but on deployment day 1 it
-- doesn't exist yet, so the intraday scan is skipped all day — not a bug, but the intended
-- fail-safe behind the "never fabricate a value" principle (design doc §10). This script fills
-- that day-1 gap by manually seeding one initial snapshot.
--
-- ⚠️ MOCK 모드 전용. LIVE 계좌 잔고는 실제 조회 API(kt00001/kt00018)로만 산정해야 하며 이 스크립트로
--    임의 시딩하면 안 된다 — 호출용 셸 스크립트(seed-initial-snapshot.sh)가 이를 강제로 막는다.
-- ⚠️ MOCK mode only. LIVE account balance must only ever come from the real lookup APIs
--    (kt00001/kt00018) — never seeded arbitrarily via this script. The wrapper shell script
--    (seed-initial-snapshot.sh) enforces this.
--
-- 사용법 (Usage): 직접 실행하지 말고 seed-initial-snapshot.sh를 통해 실행할 것
-- (@account_id/@trading_mode/@initial_capital 세션 변수를 그 스크립트가 미리 설정해준다)
-- (Prefer running via seed-initial-snapshot.sh, not directly — it sets the
--  @account_id/@trading_mode/@initial_capital session variables this script reads)

INSERT INTO trading_account_snapshot (account_id, trading_mode, snapshot_date, total_value, cash_balance, daily_pnl, daily_pnl_rate)
VALUES (@account_id, @trading_mode, CURRENT_DATE, @initial_capital, @initial_capital, 0, 0)
ON DUPLICATE KEY UPDATE
    total_value = VALUES(total_value),
    cash_balance = VALUES(cash_balance),
    daily_pnl = VALUES(daily_pnl),
    daily_pnl_rate = VALUES(daily_pnl_rate),
    deleted_at = NULL;
