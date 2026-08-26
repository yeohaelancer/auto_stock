#!/usr/bin/env bash
#
# 배포 첫날 초기 계좌 스냅샷을 1건 심는다 — postMarketJob이 아직 한 번도 안 돈 상태에서
# intradaySignalScan()이 종일 스킵되는 것을 막기 위함 (자세한 배경은 seed-initial-snapshot.sql 참고).
# Seeds one initial account snapshot on deployment day 1 — prevents intradaySignalScan() from being
# skipped all day before postMarketJob has ever run once (background in seed-initial-snapshot.sql).
#
# ⚠️ MOCK 모드에서만 사용할 것. LIVE 잔고를 임의로 지어내지 않기 위해 TRADING_MODE=LIVE면 즉시 중단한다.
# ⚠️ MOCK mode only. Aborts immediately if TRADING_MODE=LIVE, to never fabricate a LIVE balance.
#
# 사용법 (Usage):
#   backend와 같은 환경변수(.env)를 로드한 셸에서: ./scripts/seed-initial-snapshot.sh
#   Run from a shell with the same environment variables (.env) the backend uses.

set -euo pipefail
cd "$(dirname "$0")"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-JDWORKS}"
DB_USER="${DB_USER:-jdwadmin}"

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "❌ DB_PASSWORD 환경변수가 설정되지 않았습니다. (.env를 먼저 로드하세요)" >&2
    echo "❌ DB_PASSWORD is not set. Load your .env first." >&2
    exit 1
fi

ACCOUNT_ID="${KIWOOM_ACCOUNT_NO:-}"
TRADING_MODE="${TRADING_MODE:-MOCK}"
INITIAL_CAPITAL="${MOCK_INITIAL_CAPITAL:-10000000}"

if [ "$TRADING_MODE" = "LIVE" ]; then
    echo "❌ TRADING_MODE=LIVE 상태에서는 이 스크립트를 실행할 수 없습니다." >&2
    echo "   LIVE 계좌 잔고는 실제 조회 API(kt00001/kt00018)로만 산정해야 하며, 임의 값을 심으면 안 됩니다." >&2
    echo "❌ Cannot run this script while TRADING_MODE=LIVE." >&2
    echo "   LIVE balance must only come from the real lookup APIs (kt00001/kt00018) — never seeded." >&2
    exit 1
fi

echo "▶ 초기 계좌 스냅샷 시딩: account='${ACCOUNT_ID}', mode=${TRADING_MODE}, capital=${INITIAL_CAPITAL}"
echo "▶ Seeding initial account snapshot: account='${ACCOUNT_ID}', mode=${TRADING_MODE}, capital=${INITIAL_CAPITAL}"

# mysql 클라이언트는 psql의 ":var" 파일 변수 치환이 없으므로, 세션 변수(SET @var=...)를 먼저 정의한
# 다음 seed-initial-snapshot.sql이 그 값을 읽어 쓰도록 표준입력으로 이어붙인다.
# The mysql client has no psql-style ":var" file substitution, so we define session variables
# (SET @var=...) first, then pipe seed-initial-snapshot.sql in via stdin to read them.
MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" "$DB_NAME" <<SQL
SET @account_id = '${ACCOUNT_ID}';
SET @trading_mode = '${TRADING_MODE}';
SET @initial_capital = ${INITIAL_CAPITAL};
SOURCE seed-initial-snapshot.sql
SQL

echo "✅ 완료 — 오늘부터 intradaySignalScan()이 스킵되지 않습니다."
echo "✅ Done — intradaySignalScan() will no longer be skipped from today."
