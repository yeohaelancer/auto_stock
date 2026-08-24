# 💻 Dev Agent 구현 결과

> 최종 업데이트: 2026-08-24

## 구현 계획
- **아키텍처**: DBA 스키마 + Designer 위젯 명세를 기반으로, 설계 문서 §8 모듈 구조를 그대로 따르는 Spring Boot 백엔드 / 별도 프로세스 FastAPI ML 서비스 / Vue 3 위젯 3계층 구조
- **기술 스택**: Spring Boot 3.3 (Java 21) + MyBatis + PostgreSQL, Python FastAPI(LightGBM 예정), Vue 3 (Composition API)
- **작업 순서**: Phase 1 범위(키움 연동 골격, DB 매퍼 1종, 모의투자 주문, 리스크 엔진 골격, 대시보드 API, 위젯 골격)까지 구현. 실제 키움 REST API 호출/인증, 학습된 모델은 TODO로 명시 (공식 문서 확인 및 실데이터 필요)

## 🆕 종목 유니버스 갱신 배치 (preMarketJob 실구현)
- `StockMasterMapper.markStaleAsHalted`/`markActiveAsResumed` 신규: 설정된 기준일(`trading.universe.stale-trading-days`, 기본 5일) 동안 `price_history`에 시세가 없는 종목을 거래정지 후보로 표시, 다시 시세가 관측되면 자동 해제
- ⚠️ 명확한 한계: 이는 "최근 시세 부재"라는 대리 신호(휴리스틱)이지 키움/KRX의 실제 거래정지 통지가 아님. `is_managed`(관리종목 지정)는 자체 데이터로 판단 불가능한 외부 지정 정보라 **이 배치에서 다루지 않음** — KRX/공시 API 연동 필요(TODO로 명시)

## 🆕 백테스트 프레임워크 (설계 §4.5)
- `BacktestEngine`: 순수 계산 엔진. 거래 목록을 받아 누적수익률·MDD·승률·샤프비율을 계산, 매수+매도 양방향 거래대금 기준 수수료/슬리피지(`trading.backtest.commission-rate/slippage-rate`) 반영 — 설계 §4.5의 5개 필수 지표 모두 충족
- `BacktestService`: `order_log`의 체결(FILLED) 이력을 종목별 FIFO로 매수-매도 쌍(`BacktestTrade`)으로 구성해 엔진에 전달 — **과거 시세 재생 시뮬레이션이 아니라, 실제/모의로 쌓인 체결 기록의 사후 분석**
- `GET /api/backtest/performance?tradingMode=MOCK` 엔드포인트로 조회 가능
- 알려진 제한: 자본 곡선은 복리가 아닌 단리 근사, 샤프비율은 무위험수익률 0·비연율화 단순화 — 코드 Javadoc에 명시. Phase 1 시점에는 실제 체결 이력이 없을 수 있어 결과가 비어있는 것이 정상(버그 아님)
- 설계 §4.5 "백테스트 결과가 기준 미달 시 실거래 전환 보류" 원칙의 판단 근거로 이 엔드포인트를 사용할 것 — 구체적 기준값(샤프비율 임계값 등)은 사용자가 직접 정할 부분

## 🔧 BUG-006 수정 — 계좌 스냅샷 저장 (postMarketJob)
- `TradingScheduler.postMarketJob()` 실구현: `OrderLogMapper.sumFilledValue()`로 누적 매수/매도 체결금액을 합산 → "초기 시드머니(`trading.mock.initial-capital`) − 누적매수 + 누적매도"로 MOCK 모드 현금 잔고 산정 → `AccountService.getPositionsValue()`(BUG-002 로직 재사용, 중복 제거)로 포지션 평가액 합산 → `AccountSnapshotMapper.upsert()`로 저장
- Review 🔴 필수 반영사항 반영: **LIVE 모드에서는 스냅샷을 절대 계산/저장하지 않고 스킵** — 이 계산법(시드머니 기반 역산)은 시스템이 전 자금 흐름을 통제하는 MOCK 모드에서만 정당하며, LIVE에 적용하면 리스크 엔진이 허구의 잔고로 판단하게 됨
- `AccountSnapshotMapper.upsert()`는 `ON CONFLICT`로 동일 날짜 재실행 시 안전하게 갱신
- 알려진 제한: LIVE 모드 잔고 조회는 여전히 TODO — 실제 키움 계좌 조회 API 연동 필요

## 🔧 BUG-003 수정 — AI 예측 서비스 실모델 연동
- `ml-service/train.py` 신규: LightGBM 분류 모델 학습 스크립트. **합성(synthetic) 라벨로만 학습** — 실제 시세 이력 기반 정답 라벨이 아직 없기 때문이며, `modelVersion`을 `lgbm-synthetic-0.1`로 명시해 실거래 신호로 오인되지 않도록 함 (Review 🟡 필수 반영사항)
- `ml-service/main.py` 개편: 기동 시 `model.pkl` 로딩(없으면 `placeholder-0.0` 응답 유지), `/predict` 호출 시 `feature_daily` 테이블에서 해당 종목의 최신 실제 피처를 조회해 추론. 피처가 없는 종목은 `no-feature-data` placeholder 반환 — 임의 예측 금지 원칙 유지
- `psycopg2-binary` 의존성 추가, `docker-compose.yml`의 `ml-service`에 DB 접속 환경변수(자체 자격증명) 추가 — Review 낮음 이슈 반영
- 알려진 제한: 합성 라벨 기반 모델이므로 **실거래 신호로 사용 불가** 상태 유지. 실제 운영 전 `price_history`/`feature_daily` 실데이터 축적 → 과거 N일 후 실제 등락 라벨링 → `train.py` 데이터 소스 교체 → 재학습이 필요

## 🔧 BUG-005 수정 — 장중 스케줄러 ↔ OrderService 자동 연동
- `TradingScheduler.intradaySignalScan()` 신규 추가: 09:05~15:15 KST 5분 간격으로 종목 유니버스(`stock_master`, 관리/거래정지 제외)를 순회하며 `OrderService.processSignal`을 자동 호출
- `StockMasterMapper.findActiveUniverse()`, `AccountSnapshotMapper.findLatest()`(+ 각 XML) 신규 작성
- Review 필수 반영사항 2건 모두 구현:
  - 🔴 종목별 예외를 `try/catch`로 격리 — 한 종목 실패가 나머지 종목 스캔을 막지 않음
  - 🟡 최근 `account_snapshot`이 없으면 현금/손실률을 알 수 없으므로 **사이클 전체를 스킵** (임의값 대체 금지, 설계 §10)
- 알려진 제한: `postMarketJob()`이 아직 `account_snapshot`을 실제로 저장하지 않아(TODO), 최초 배포 시 스냅샷이 쌓이기 전까지는 `intradaySignalScan()`이 계속 스킵됨 — 운영 전 최소 1건의 초기 스냅샷 시딩 또는 `postMarketJob` 구현이 필요

## 🔧 BUG-004 수정 — 신호→리스크→주문 오케스트레이션 서비스
- 신규 `OrderService.processSignal(stockCode, cashBalance, currentDailyLossRate)` 구현: `SignalEngine` → `RiskEngine.validate`(신호 단계) → 시세 조회 → 포지션 사이징(§5.2 고정비율) → `RiskEngine.validateOrder`(주문 단계) → `OrderExecutor.execute` → `OrderLogMapper.insert`로 이어지는 전체 파이프라인을 하나의 서비스로 연결
- 시세 조회 실패(키움 API 미연동 상태 포함) 시 **임의 가격으로 대체하지 않고 매매 자체를 스킵** — Review 사전 점검 지적사항 반영 (설계 §10)
- 리스크 엔진이 주문을 거부하면 `RiskEventMapper`로 `risk_log`에 기록하고 생성된 ID를 `order_log.blocked_by_risk_id`에 연결 — 감사 추적 확보 (Review 사전 점검 지적사항 반영)
- `KiwoomMarketClient`의 실제 빈이 없어 애플리케이션이 기동조차 되지 않던 구조적 공백을 `KiwoomMarketRestClient` 골격 구현체로 메움 — 연동 전까지 `isConnected()=false`, 빈 시세 목록을 정직하게 반환 (거짓 연결 상태 보고 금지)
- `TradingController`에 `POST /api/trading/signals/{stockCode}/process` 수동 트리거 엔드포인트 추가 — 장중 스케줄러 자동 연동 전 QA/수동 검증용

## 🔧 QA 버그 수정 (BUG-001, BUG-002)
- **BUG-001**: `PositionMapper`(+ `PositionMapper.xml`) 신규 작성, `AccountService.getPositions`를 실제 매퍼 호출로 연결 — `accountId`+`tradingMode` 둘 다 필수 파라미터로 모의/실전 혼동을 원천 차단
- **BUG-002**: `RiskEngine.validateOrder(OrderLog, AccountRiskContext)` 신규 추가 — 종목당 포지션 한도(`maxPositionRatioPerStock`), 최소 현금 비율(`minCashRatio`)을 실제 계산 로직으로 구현. BUY 주문에만 적용(SELL은 노출을 줄이므로 대상 아님), 계좌 평가금액을 알 수 없으면 안전하게 차단(fail-safe)
- `AccountService.getRiskContext(...)`로 `AccountRiskContext`(평가금액/현금잔고/대상종목 평가액)를 구성 — 현재는 평균단가 기준 근사치이며, 실시간 현재가 반영은 `KiwoomMarketClient` 연동 후 개선 예정(TODO로 명시)
- 두 수정 모두 SignalEngine→RiskEngine→OrderExecutor를 잇는 오케스트레이션 서비스(예: `OrderService`)에서 호출되어야 완결되며, 해당 오케스트레이션 클래스는 아직 미구현 — 다음 스프린트 항목으로 QA에 전달

## 반영한 Review 지적사항
- 🟡 긴급정지 경로: `TradingController.emergencyStop()` → `RiskEngine.triggerEmergencyStop()`이 상태 전환 + `RiskEvent` 반환을 한 트랜잭션 흐름으로 처리 (`risk_log` insert 연동은 TODO)
- 🟡 리스크 게이지 데이터 소스 분리: `RiskEngine`은 장중 실시간 손실률을 파라미터로 받아 검증하고, `account_snapshot`은 스케줄러의 장마감 정산에서만 사용하도록 역할 분리
- ✅ `trading_mode` 필터링: `OrderLogMapper.findRecentByMode`, `AccountService.getPositions` 모두 모드를 필수 파라미터로 받음

## 백엔드 구현 (Spring Boot)
경로: [`backend/src/main/java/com/jdwork/autotrading/`](../../../backend/src/main/java/com/jdwork/autotrading)

```
config/       TradingModeConfig, KiwoomApiProperties
market/       KiwoomMarketClient(인터페이스), PriceBar DTO
prediction/   PredictionClient(WebClient, FastAPI 호출), PredictionResult DTO
strategy/     SignalEngine, StrategySignal 도메인
risk/         RiskEngine(전략과 완전 독립), RiskEvent 도메인
order/        OrderExecutor 인터페이스, MockOrderExecutor/LiveOrderExecutor(설정값 기반 스위칭), OrderLogMapper(+XML)
account/      AccountService, Position 도메인
scheduler/    TradingScheduler (장전/장마감/야간 배치)
api/          TradingController (/api/trading/status, /positions, /emergency-stop)
```

빌드 설정: [`backend/build.gradle`](../../../backend/build.gradle), 실행 설정: [`backend/src/main/resources/application.yml`](../../../backend/src/main/resources/application.yml)

## AI 예측 서비스 (FastAPI)
경로: [`ml-service/main.py`](../../../ml-service/main.py), [`ml-service/requirements.txt`](../../../ml-service/requirements.txt)
- `GET /health`, `POST /predict` 엔드포인트 골격 구현. 현재는 배선 검증용 더미 응답 — **실거래 신호로 사용 금지** (모델 학습 전)

## 프론트엔드 구현 (Vue 3)
경로: [`frontend/components/`](../../../frontend/components)
- `AutoTradingWidget.vue`(메인 위젯), `EmergencyStopButton.vue`(확인 다이얼로그 포함), `RiskGauge.vue`
- Designer 컬러 토큰(`--mode-mock-bg`, `--mode-live-bg`, `--risk-*`)을 CSS 변수로 참조 — 실제 값은 기존 JD WORK 토큰 파일과 매핑 필요 (Review 낮음 이슈, 블로킹 아님)
- `PositionCard`, `SignalFeedItem`, `OrderLogRow`, `PerformanceChart`는 위젯 골격에 아직 미포함 — 다음 스프린트 항목 (아래 알려진 제한사항 참고)

## 단위 테스트
1차 스켈레톤 단계로 단위 테스트는 미포함. QA 단계에서 아래 "검증이 필요한 엣지 케이스"를 수동/통합 테스트로 우선 검증하고, 이후 스프린트에서 `RiskEngine`/`SignalEngine`에 대한 JUnit 테스트를 추가한다.

## 실행 방법
```bash
# 백엔드
cd backend && ./gradlew bootRun

# AI 예측 서비스 (최초 1회 학습 필요 — 없으면 placeholder 응답만 반환)
cd ml-service && pip install -r requirements.txt
python train.py                       # model.pkl 생성 (합성 데이터 기반)
uvicorn main:app --reload --port 8001
```

## 📤 QA Agent 전달 사항
- **테스트 가능한 엔드포인트**: `GET /api/trading/status`, `GET /api/trading/positions`, `POST /api/trading/emergency-stop`, FastAPI `GET /health`, `POST /predict`
- **알려진 제한사항**:
  - 키움 REST API 실제 인증/시세/주문 연동 미구현 (`KiwoomMarketRestClient`, `LiveOrderExecutor`/`MockOrderExecutor`의 TODO 부분) — 현재 `isConnected()=false`, 시세 항상 빈 목록이므로 `OrderService.processSignal`은 항상 "시세 미확보로 스킵" 경로를 탐
  - `AccountRiskContext`의 평가금액이 평균단가 기준 근사치 — 실시간 현재가 미반영 (KiwoomMarketClient 연동 후 개선 예정)
  - `cashBalance`(현금 잔고) 실시간 조회 API 미연동 — `OrderService.processSignal` 호출부에서 값을 직접 전달해야 함 (TODO)
  - LIVE 모드 계좌 스냅샷은 여전히 저장되지 않음 (의도됨) — 실제 잔고 조회 API 연동 후 별도 구현 필요
  - `ml-service`에 `model.pkl`이 없으면(최초 배포 시 기본 상태) `/predict`는 여전히 `placeholder-0.0` 더미 응답 — `python train.py` 실행 후에야 실제 추론 시작
  - AI 모델이 합성 라벨로 학습되어 있어 `feature_daily`에 실데이터가 있어도 예측 품질은 무의미함(배선 검증 목적) — 실거래 신호로 사용 금지 상태 유지
  - 백테스트는 과거 시세 재생 시뮬레이션이 아니라 실제 체결 이력 기반 사후 분석 — 체결 이력이 쌓이기 전(Phase 1 초기)에는 빈 결과 반환
  - `is_managed`(관리종목) 자동 갱신 미구현 — KRX/공시 API 연동 필요
  - `PositionCard`/`SignalFeedItem`/`OrderLogRow`/`PerformanceChart` Vue 컴포넌트 미구현
- **특별 검증 필요 엣지 케이스**:
  - `RiskEngine.isEmergencyStopped()`가 true인 상태에서 `SignalEngine`이 신호를 만들어도 주문까지 이어지지 않는지
  - `trading.mode=LIVE`로 설정 시 `LiveOrderExecutor` 빈만 등록되고 `MockOrderExecutor`는 등록되지 않는지 (Spring 조건부 빈 검증)
  - `PredictionClient` 호출 실패(타임아웃) 시 `SignalEngine.generateSignal`이 예외를 전파하지 않고 `Optional.empty()`를 반환하는지
