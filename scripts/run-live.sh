#!/usr/bin/env bash
# 실거래(LIVE) 모드로 백엔드를 띄운다 — 저장소 루트의 .env.live를 로드한 뒤 gradlew bootRun 실행.
# Launches the backend in LIVE (real trading) mode — loads .env.live from the repo root, then runs gradlew bootRun.
#
# ⚠️ 실전 계좌로 실제 주문이 나간다. 오조작 방지를 위해 기동 전 확인을 요구한다
#    (비대화형 환경에서는 CONFIRM_LIVE=1로 우회 가능).
# ⚠️ Real orders are placed against a real account. Requires an explicit confirmation before
#    launch to prevent accidental starts (bypass with CONFIRM_LIVE=1 in non-interactive environments).
#
# 사용법 (Usage): ./scripts/run-live.sh
# 사전 준비 (Prerequisite): cp .env.live.example .env.live 후 실전용 키움 앱키로 값 채우기
#                         (fill in .env.live with real LIVE Kiwoom credentials first)

set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env.live ]; then
    echo "❌ .env.live가 없습니다. .env.live.example을 복사해 먼저 값을 채우세요." >&2
    echo "❌ .env.live not found. Copy .env.live.example and fill in the values first." >&2
    exit 1
fi

set -a
source .env.live
set +a

if [ "${TRADING_MODE:-}" != "LIVE" ]; then
    echo "❌ .env.live의 TRADING_MODE가 LIVE가 아닙니다 (현재: ${TRADING_MODE:-미설정}). 파일을 확인하세요." >&2
    echo "❌ TRADING_MODE in .env.live is not LIVE (current: ${TRADING_MODE:-unset}). Check the file." >&2
    exit 1
fi

if [ "${CONFIRM_LIVE:-}" != "1" ]; then
    echo "⚠️  실거래(LIVE) 모드로 기동하려 합니다 — 실제 계좌(${KIWOOM_ACCOUNT_NO:-미설정})로 진짜 주문이 나갑니다."
    echo "⚠️  About to start in LIVE mode — real orders will be placed against account ${KIWOOM_ACCOUNT_NO:-<unset>}."
    read -r -p "계속하려면 'LIVE'를 입력하세요 (type 'LIVE' to continue): " confirm
    if [ "$confirm" != "LIVE" ]; then
        echo "취소되었습니다. (Cancelled.)"
        exit 1
    fi
fi

echo "▶ LIVE 모드로 백엔드를 기동합니다 (DB=${DB_HOST}:${DB_PORT}/${DB_NAME})"
echo "▶ Starting the backend in LIVE mode (DB=${DB_HOST}:${DB_PORT}/${DB_NAME})"
cd backend
./gradlew bootRun
