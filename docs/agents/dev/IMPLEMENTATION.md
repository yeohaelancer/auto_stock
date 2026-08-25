# 💻 Dev Agent 구현 결과

> 최종 업데이트: 2026-08-24

## 구현 계획
- **아키텍처**: DBA 스키마 + Designer 위젯 명세를 기반으로, 설계 문서 §8 모듈 구조를 그대로 따르는 Spring Boot 백엔드 / 별도 프로세스 FastAPI ML 서비스 / Vue 3 위젯 3계층 구조
- **기술 스택**: Spring Boot 3.3 (Java 21) + MyBatis + PostgreSQL, Python FastAPI(LightGBM 예정), Vue 3 (Composition API)
- **작업 순서**: Phase 1 범위(키움 연동 골격, DB 매퍼 1종, 모의투자 주문, 리스크 엔진 골격, 대시보드 API, 위젯 골격)까지 구현. 실제 키움 REST API 호출/인증, 학습된 모델은 TODO로 명시 (공식 문서 확인 및 실데이터 필요)

## 🆕 키움증권 OAuth 접근토큰 발급 실연동
- `KiwoomTokenClient` 신규: `POST /oauth2/token`(grant_type=client_credentials, appkey/secretkey) 실호출, 응답의 `expires_dt`를 파싱해 만료 5분 전까지 캐시된 토큰을 재사용하고 이후 자동 재발급
- 요청/응답 필드는 **키움 공식 개발자 포털 문서로 확인 후 구현** (2026-08-24, "접근토큰발급(au10001)" 문서) — Base URL(`api.kiwoom.com`/`mockapi.kiwoom.com`)이 기존 `application.yml` 설정과 정확히 일치함을 재확인
- `KiwoomMarketRestClient.isConnected()`가 이제 실제 토큰 발급 성공 여부로 판단 — 더 이상 하드코딩된 `false`가 아님
## 🆕 키움증권 일봉 시세 조회 실연동 (TR: ka10086 일별주가요청)
- `KiwoomMarketRestClient.getRecentPriceBars()` 실구현: 사용자가 포털에서 확인해 전달한 스펙 기준
  - `POST /api/dostk/mrkcond`, Header `authorization: Bearer {token}` + `api-id: ka10086`, Body `{stk_cd, qry_dt, indc_tp}`
  - 응답 `daly_stkpc` 리스트를 `PriceBar`로 매핑, 날짜 기준 최신순 정렬 후 `count`만큼 반환 (API 응답 순서를 신뢰하지 않고 직접 정렬)
  - 조회 실패/파싱 실패 시 예외를 삼키고 빈 목록 반환 — 임의 값 대체 금지 원칙 유지
- 거래량(`trde_qty`)까지 매핑 완료(부호 없는 필드, 방어적으로 `+` 접두사만 제거 후 파싱)
- 알려진 제한: **분봉(MINUTE) TR 미검증** — 요청 시 빈 목록 반환
## 🆕 키움증권 실거래 주문 실연동 (TR: kt10000 매수주문 / kt10001 매도주문)
- `LiveOrderExecutor` 실구현: `POST /api/dostk/ordr`, Header `api-id: kt10000`(매수)/`kt10001`(매도), Body `{dmst_stex_tp, stk_cd, ord_qty, ord_uv, trde_tp}` — 매수·매도 둘 다 사용자가 포털에서 확인해 전달한 스펙 기준으로 구현·검증 완료 (두 TR이 필드 구조 동일함을 확인)
- `trde_tp="0"`(보통/지정가)로 고정 — `OrderService`가 항상 지정가를 산정하는 현재 구조와 일치
- 응답의 `ord_no`(주문번호)만 확인하고 **체결 여부는 별도 TR(미검증)로 확인해야 하므로 임의로 FILLED 처리하지 않고 PENDING 유지**
## 🆕 과다매매(잦은 재매매) 방지 — 사용자 요청(수수료 부담 완화)
- `RiskEngine.checkOvertrading(stockCode, tradingMode)` 신규: 설계 §6 "이상 매매 감지" 항목 구현
  1. **종목별 쿨다운**: 최근 주문 후 설정 거래일수(기본 3일, `COOLDOWN_TRADING_DAYS`)가 지나기 전에는 같은 종목 재매매 차단. ⚠️ 거래일을 달력일로 근사(주말/공휴일 미반영)
  2. **일일 최대 거래 횟수**: 계좌 전체 기준 하루 거래 건수가 한도(기본 5회, `MAX_DAILY_TRADES`) 도달 시 신규 주문 차단
- `OrderService.processSignal()`의 신호 단계 검증 직후(가격 조회 전)에 삽입 — 불필요한 시세 조회/포지션 사이징 계산을 아끼는 효과도 있음
- `OrderLogMapper.findLastOrderTime`/`countOrdersSince` 신규
- **명시적으로 하지 않은 것**: "기대수익이 수수료를 못 넘으면 거래 안 함" 필터는 추가하지 않음 — AI 예측의 `expectedReturn` 필드가 현재 항상 null이라(실측 기반 근거 없음) 신뢰할 수 없는 값으로 필터링하면 오히려 오판단을 부를 수 있어 보류. AI 모델이 실제 기대수익률을 출력하게 되면 추가 검토

## 🧪 실제 기동 테스트 (2026-08-24) — 발견 및 수정한 실버그
`./gradlew wrapper`로 Gradle Wrapper를 신규 생성하고, 로컬 PostgreSQL 16(포터블 바이너리)에 `schema.sql`을 적용한 뒤 실제로 `bootRun`, REST API 호출, DB 조회까지 end-to-end로 검증했다. 지금까지 한 번도 실제 컴파일/기동을 해본 적이 없어서 아래 4건이 숨어 있었다 — 전부 이번에 발견·수정 완료:

1. **컴파일 에러**: `RiskEngine.RiskCheckResult`의 정적 팩토리 메서드 `approved()`가 레코드 컴포넌트 접근자 `approved()`와 이름이 겹쳐 컴파일 자체가 안 됨 → `approve()`로 개명
2. **런타임 에러(MyBatis)**: `java.util.UUID` 파라미터(`order_id`, `signal_id`)에 대한 타입 핸들러가 없어 `OrderLogMapper.xml` 파싱이 기동 시점에 실패 → `UuidTypeHandler` 신규 작성 + `mybatis.type-handlers-package` 등록
3. **로직 버그**: `TradingController.emergencyStop()`이 Javadoc에는 "risk_log 기록"이라고 적혀 있었지만 실제로는 `RiskEventMapper.insert()`를 호출하지 않아 긴급정지 이벤트가 DB에 전혀 남지 않고 있었음 → 수정 후 실제 DB 조회로 `risk_log`에 저장됨을 확인
4. **운영 이슈**: 콘솔 로그의 한글 메시지가 Windows 콘솔 인코딩 문제로 깨져 표시됨 → `application.yml`에 `logging.charset.console/file: UTF-8` 명시

검증한 것: `GET /api/trading/status`, `GET /api/trading/positions`, `GET /api/backtest/performance`, `POST /api/trading/emergency-stop`(+ DB 반영 확인), `POST /api/trading/signals/{stockCode}/process`(ml-service 미기동 시 정상적으로 신호 스킵 확인) — 모두 기대대로 동작.

## 🆕 AI 모델 합성 데이터 → 실데이터 전환
- `PriceHistoryMapper`(+XML) 신규: `price_history` upsert(`uq_price_history` 유니크 인덱스 기반), 최근 N개 봉 조회
- `FeatureDaily` 도메인 + `FeatureDailyMapper`(+XML) 신규: `feature_daily` upsert
- `FeatureEngineeringService` 신규: 표준 공식으로 지표 계산 — SMA(MA5/MA20), RSI14(단순평균 방식), MACD(EMA12−EMA26), 볼린저밴드(MA20±2σ). 각 지표는 필요한 최소 데이터가 부족하면 임의 값 대신 null로 남김(모두 nullable 컬럼)
- `TradingScheduler.collectPriceHistoryAndFeatures()` 신규: 16:00 KST(장마감 정산 이후) 종목 유니버스 전체를 순회하며 키움 TR(ka10086)로 최근 60일치 시세를 한 번에 받아 `price_history`에 저장 → 지표 계산 → `feature_daily` 저장. 종목별 예외 격리(기존 스케줄러 원칙과 동일)
- `ml-service/train.py` 전면 개편: `feature_daily`를 `price_history`와 조인해 **"N거래일 후 실제 등락"** 기반 정답 라벨을 산출하는 실데이터 학습 파이프라인으로 전환
  - 실데이터가 최소 200건 이상이면 실데이터로 학습, `modelVersion=lgbm-real-h5d-n{건수}`로 태깅
  - 실데이터 부족(운영 초기 등) 시 기존 합성 데이터로 자동 폴백, `modelVersion=lgbm-synthetic-0.1` 유지 — 실거래 신호 오인 방지 원칙 계속 적용
- 알려진 제한: 방금 배치를 연결했을 뿐이라 아직 실데이터가 쌓이지 않은 상태 — 며칠~몇 주 운영해 데이터가 축적된 후에야 `train.py`가 실데이터 경로를 타게 됨 (버그 아님, 시간이 필요한 항목)

## 🆕 키움증권 LIVE 계좌 잔고/보유종목 실연동 (TR: kt00001 예수금상세현황 + kt00018 계좌평가잔고내역)
- `KiwoomCashBalanceClient` 신규: `POST /api/dostk/acnt`, `api-id: kt00001` — 응답의 `entr`(예수금) 필드를 실제 현금 잔고로 그대로 사용
- `KiwoomBalanceClient` (kt00018): 보유종목 리스트·평가액(`tot_evlt_amt`) 조회로 역할 축소 — 처음 시도했던 "추정예탁자산 − 총평가금액" 역산 추론은 kt00001의 명시적 `entr` 필드 확인 후 완전히 폐기
- `TradingScheduler.settleLiveSnapshot()`: 두 TR을 조합해 `totalValue = 예수금 + 보유종목평가액`으로 저장, 둘 중 하나라도 실패하면 스냅샷 자체를 저장하지 않음(부분값 저장 금지, 설계 §10)
- `PositionMapper.upsertFromBalance()`: 실계좌 보유종목으로 `position` 테이블 동기화 (청산된 종목은 quantity=0으로 반영되어 기존 조회 필터가 자연스럽게 숨김)
- ✅ 이전 세션에서 남겼던 "LIVE 현금 잔고는 추론값" 경고는 이제 **해소됨** — 명시적 `entr` 필드로 대체

## 🆕 키움증권 체결 확인 실연동 (TR: ka10076 체결요청)
- `KiwoomFillInquiryClient` 신규: `POST /api/dostk/acnt`, `api-id: ka10076`, 종목코드로 최근 체결 목록을 받아 `kiwoomOrderNo`와 일치하는 항목을 찾아 상태(`ord_stt`: 접수/확인/체결) 판정
  - ⚠️ 요청의 `ord_no`는 특정 주문 필터가 아니라 "이 번호보다 과거" 페이징 커서임을 문서로 확인 — 그래서 목록을 받아온 뒤 클라이언트 측에서 주문번호로 매칭하는 방식으로 구현
- `TradingScheduler.checkPendingFills()` 신규: LIVE 모드에서 1분 간격으로 PENDING/PARTIAL 주문을 순회하며 체결 확인 → 체결 시 `execution_status=FILLED`(또는 부분체결 시 `PARTIAL`) + `executed_price` 갱신
- `OrderLogMapper.findUnresolvedLiveOrders`/`updateFillStatus` 신규
- 종목별 예외를 격리해(try/catch) 한 주문의 조회 실패가 나머지 주문 확인을 막지 않도록 함 (기존 스케줄러 원칙과 동일하게 적용)
- 알려진 제한: MOCK 모드는 이 배치를 타지 않음(즉시체결 시뮬레이션이라 불필요) — LIVE 전환 후에만 의미 있음, LIVE 자체는 여전히 미승인 상태(Phase 5 이전)

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
python train.py                       # model.pkl 생성 (실데이터 200건 이상이면 실데이터, 아니면 합성 데이터로 자동 폴백)
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
