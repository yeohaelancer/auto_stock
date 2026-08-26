#!/usr/bin/env bash
# 모의투자(MOCK) 모드로 백엔드를 띄운다 — 저장소 루트의 .env.mock을 로드한 뒤 gradlew bootRun 실행.
# Launches the backend in MOCK (paper trading) mode — loads .env.mock from the repo root, then runs gradlew bootRun.
#
# 사용법 (Usage): ./scripts/run-mock.sh
# 사전 준비 (Prerequisite): cp .env.mock.example .env.mock 후 값 채우기 (fill in .env.mock first)

set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env.mock ]; then
    echo "❌ .env.mock이 없습니다. .env.mock.example을 복사해 먼저 값을 채우세요." >&2
    echo "❌ .env.mock not found. Copy .env.mock.example and fill in the values first." >&2
    exit 1
fi

set -a
source .env.mock
set +a

if [ "${TRADING_MODE:-}" != "MOCK" ]; then
    echo "❌ .env.mock의 TRADING_MODE가 MOCK이 아닙니다 (현재: ${TRADING_MODE:-미설정}). 파일을 확인하세요." >&2
    echo "❌ TRADING_MODE in .env.mock is not MOCK (current: ${TRADING_MODE:-unset}). Check the file." >&2
    exit 1
fi

echo "▶ MOCK 모드로 백엔드를 기동합니다 (DB=${DB_HOST}:${DB_PORT}/${DB_NAME})"
echo "▶ Starting the backend in MOCK mode (DB=${DB_HOST}:${DB_PORT}/${DB_NAME})"
cd backend
./gradlew bootRun
