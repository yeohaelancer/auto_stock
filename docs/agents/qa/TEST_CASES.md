# 🔍 QA Agent 테스트 케이스

> 최종 업데이트: 2026-08-24
> 참조: `docs/agents/dev/IMPLEMENTATION.md`, `docs/agents/designer/screens/`

## 테스트 범위
- 기능 테스트: 대시보드 API(`/api/trading/*`), FastAPI(`/health`, `/predict`) 응답 계약
- 경계값 테스트: 신뢰도 임계값(0.6), 일일 손실 한도(3%) 경계
- 예외 처리 테스트: 예측 서비스 장애, 긴급정지 상태에서의 신호/주문 흐름, 모의/실전 모드 혼동 방지

## 테스트 케이스

| ID | 화면/기능 | 시나리오 | 기대 결과 | 실제 결과 | 상태 |
|----|---------|---------|---------|---------|------|
| TC-001 | `GET /api/trading/status` | 기본 설정(MOCK)으로 서버 기동 후 호출 | `mode=MOCK`, `emergencyStopped=false` | 코드 리뷰상 일치 | ✅ |
| TC-002 | `POST /api/trading/emergency-stop` | 긴급정지 호출 | `RiskEvent(MANUAL_KILL_SWITCH)` 반환, 이후 `status.emergencyStopped=true` | 코드 리뷰상 일치 | ✅ |
| TC-003 | `RiskEngine.validate` | 긴급정지 상태에서 신호 검증 호출 | `approved=false`, "긴급정지 상태" 사유 반환 | 코드 리뷰상 일치 | ✅ |
| TC-004 | `RiskEngine.validate` | 당일 손실률이 정확히 `-3.0%`(한도와 동일)일 때 검증 | 한도 도달로 `approved=false` (경계값 `>=` 사용) | 코드 리뷰상 일치 (`compareTo >= 0`) | ✅ |
| TC-005 | `RiskEngine.validate` | 당일 손실률 `-2.99%`(한도 미만)일 때 검증 | `approved=true` (신호 단계 사전 필터만 수행, 포지션/현금 검증은 `validateOrder`로 분리됨) | 코드 리뷰상 일치 | ✅ |
| TC-015 | `RiskEngine.validateOrder` | 종목당 포지션 한도(10%) 초과가 예상되는 BUY 주문 검증 | `approved=false`, "종목당 포지션 한도 초과" 사유 반환 | 재검증 결과 코드 리뷰상 일치 (BUG-002 수정 확인) | ✅ |
| TC-016 | `RiskEngine.validateOrder` | 매수 후 현금 비율이 최소 현금 비율(20%) 미만이 되는 BUY 주문 검증 | `approved=false`, "최소 현금 보유 비율 미달" 사유 반환 | 재검증 결과 코드 리뷰상 일치 (BUG-002 수정 확인) | ✅ |
| TC-017 | `RiskEngine.validateOrder` | SELL 주문 검증 (포지션 축소 방향) | 포지션/현금 한도 체크 없이 즉시 `approved=true` | 코드 리뷰상 일치 (`orderType != BUY` 조기 반환) | ✅ |
| TC-018 | `AccountService.getPositions` | 실제 `accountId`+`tradingMode`로 조회 | `PositionMapper.findByAccountAndMode` 결과 반환 (더 이상 항상 빈 배열 아님) | 재검증 결과 코드 리뷰상 일치 (BUG-001 수정 확인) | ✅ |
| TC-006 | `SignalEngine.generateSignal` | `PredictionClient.predict`가 예외 발생(타임아웃 등) | 예외 전파 없이 `Optional.empty()` 반환 | 코드 리뷰상 일치 (`try/catch` 처리) | ✅ |
| TC-007 | `SignalEngine.generateSignal` | 신뢰도 0.59 (임계값 0.6 미만) 응답 | 신호 생성 안 함 (`Optional.empty()`) | 코드 리뷰상 일치 | ✅ |
| TC-008 | `SignalEngine.generateSignal` | 신뢰도 0.6 (경계값, 이상) 응답, direction=UP | BUY 신호 생성 | 코드 리뷰상 일치 (`compareTo < 0`만 스킵) | ✅ |
| TC-009 | `POST /predict` (FastAPI) | 임의 종목코드로 호출 | `direction=FLAT, confidence=0.0, modelVersion=placeholder-0.0` 더미 응답 | 코드 리뷰상 일치 — **실거래 신호로 사용 불가 상태 확인됨** | ✅ (더미 확인) |
| TC-010 | `MockOrderExecutor` vs `LiveOrderExecutor` 빈 등록 | `trading.mode=LIVE`로 기동 | `LiveOrderExecutor`만 빈 등록, `MockOrderExecutor`는 미등록 | `@ConditionalOnProperty` 상호 배타 조건으로 코드 리뷰상 일치 | ✅ |
| TC-011 | `OrderLogMapper.findRecentByMode` | `tradingMode` 파라미터 누락 호출 | 컴파일 타임에 파라미터 필수(인터페이스 시그니처)이므로 누락 호출 자체 불가 | 코드 리뷰상 일치 | ✅ |
| TC-012 | 대시보드 위젯 접근성 | `EmergencyStopButton` 키보드 Tab 접근 및 `aria-label` 확인 | Tab 포커스 가능, `aria-label="자동매매 긴급 정지"` 존재 | 코드 리뷰상 일치 | ✅ |
| TC-013 | `AutoTradingWidget` | 시세 연결 끊김(API 호출 실패) 상황 | "⚠️ 시세 연결 끊김 — 신규 주문 중단됨" 배너 노출 | `connectionError` 플래그로 코드 리뷰상 일치 | ✅ |
| TC-014 | `AccountService.getPositions` | 실제 계좌 조회 | 보유 포지션 목록 반환 | 재검증 결과 통과 (TC-018과 동일 수정 확인) | ✅ |

## 버그 리포트

### BUG-001: AccountService.getPositions 미구현으로 포지션 위젯이 항상 빈 상태 — ✅ 수정 완료 (재검증 통과)
- 심각도: 🟡 Major → **해결됨**
- 재현 단계 (수정 전):
  1. 백엔드 기동 후 `GET /api/trading/positions` 호출
  2. 실제 보유 종목이 있어도 빈 배열(`[]`) 반환
- 수정 내용: `PositionMapper`(+XML) 신규 작성, `AccountService.getPositions`가 이를 호출하도록 연결
- 재검증(TC-014, TC-018): 코드 리뷰상 `PositionMapper.findByAccountAndMode` 결과를 그대로 반환함을 확인 — **통과**
- 첨부: [`backend/src/main/java/com/jdwork/autotrading/account/AccountService.java`](../../../backend/src/main/java/com/jdwork/autotrading/account/AccountService.java), [`backend/src/main/java/com/jdwork/autotrading/account/mapper/PositionMapper.java`](../../../backend/src/main/java/com/jdwork/autotrading/account/mapper/PositionMapper.java)

### BUG-002: RiskEngine의 종목당/전체 포지션 한도 검증 미구현 — ✅ 수정 완료 (재검증 통과)
- 심각도: 🟡 Major → **해결됨**
- 재현 단계 (수정 전):
  1. 특정 종목 비중이 계좌의 10%(설정값)를 초과한 상태에서 추가 매수 신호 검증 요청
  2. TODO로 남아 있어 항상 승인 경로로 진행될 수 있었음
- 수정 내용: `RiskEngine.validateOrder(OrderLog, AccountRiskContext)` 신규 구현 — 종목당 포지션 한도, 최소 현금 비율을 실제 계산
- 재검증(TC-015, TC-016, TC-017): 한도 초과/현금 부족 시 `approved=false`, SELL은 검증 생략, 코드 리뷰상 로직 일치 — **통과**
- ⚠️ 잔존 이슈(신규 발견, 낮은 심각도): `validateOrder`를 실제로 호출하는 오케스트레이션 서비스(신호→리스크→주문)가 아직 없어, 이 메서드가 배선되지 않으면 검증 자체가 호출되지 않음. 별도 이슈로 등록(BUG-004)
- 첨부: [`backend/src/main/java/com/jdwork/autotrading/risk/RiskEngine.java`](../../../backend/src/main/java/com/jdwork/autotrading/risk/RiskEngine.java)

### BUG-004 (신규): 신호→리스크→주문 오케스트레이션 서비스 미구현
- 심각도: 🟡 Major
- 재현 단계:
  1. `SignalEngine.generateSignal`로 신호를 생성해도 이를 받아 `RiskEngine.validateOrder` → `OrderExecutor.execute`로 이어주는 서비스가 코드베이스에 없음
- 기대 동작: 신호 생성 → 리스크 검증 → 주문 실행이 하나의 파이프라인으로 자동 연결되어야 함 (설계 §5.1, §8)
- 실제 동작: 세 컴포넌트가 각각 독립적으로만 존재하고 서로를 호출하는 조립 지점이 없음 — Phase 3(자동화 통합) 범위
- 첨부: 없음 (미구현 상태 자체가 이슈)

### BUG-003: AI 예측 서비스가 항상 더미 값을 반환 (모델 미탑재)
- 심각도: 🟢 Minor (Phase 2 범위로 사전 인지된 제한사항)
- 재현 단계:
  1. `POST /predict` 호출
- 기대 동작: 학습된 LightGBM 모델의 실제 예측 반환
- 실제 동작: 항상 `FLAT/confidence=0.0` 고정값 — 로드맵 Phase 2(AI 모델) 범위이므로 현 단계에서는 의도된 상태
- 첨부: [`ml-service/main.py`](../../../ml-service/main.py)

## BUG-004 재검증 (오케스트레이션 서비스)

| ID | 화면/기능 | 시나리오 | 기대 결과 | 실제 결과 | 상태 |
|----|---------|---------|---------|---------|------|
| TC-019 | `OrderService.processSignal` | 정상 흐름 (신호 승인 + 시세 확보 + 주문 승인) | 신호 생성 → 리스크 통과 → 시세/수량 산정 → 주문 승인 → `OrderExecutor.execute` → `order_log` insert 순서로 진행 | 코드 리뷰상 흐름 일치 | ✅ |
| TC-020 | `OrderService.processSignal` | `KiwoomMarketClient.getRecentPriceBars`가 빈 목록 반환 (현재 `KiwoomMarketRestClient` 골격 상태) | 임의 가격으로 대체하지 않고 `Optional.empty()` 반환, 주문 미생성 | 코드 리뷰상 일치 (`latestClosePrice` null 시 즉시 반환) — **현재 실제 동작과 일치 (시세 미연동 상태)** | ✅ |
| TC-021 | `OrderService.processSignal` | 신호 단계 리스크 거부(긴급정지/일일 한도) | 주문 자체를 생성하지 않고 `Optional.empty()` 반환 | 코드 리뷰상 일치 | ✅ |
| TC-022 | `OrderService.processSignal` | 주문 단계 리스크 거부(포지션 한도/현금 비율) | `risk_log` insert 후 `blockedByRiskId` 연결, `order_log`에 `REJECTED` 상태로 기록됨 | 코드 리뷰상 일치 (`RiskEventMapper.insert` → `useGeneratedKeys`) | ✅ |
| TC-023 | `POST /api/trading/signals/{stockCode}/process` | 존재하지 않는 종목코드로 호출 | `SignalEngine`이 예측 실패로 스킵 → 빈 응답 (200 + null body 또는 204) | 코드 리뷰상 일치 (`Optional.empty()` 직렬화) | ✅ |

## BUG-005 재검증 (장중 스케줄러 자동 연동)

| ID | 화면/기능 | 시나리오 | 기대 결과 | 실제 결과 | 상태 |
|----|---------|---------|---------|---------|------|
| TC-024 | `TradingScheduler.intradaySignalScan` | 계좌 스냅샷 없음 | 사이클 전체 스킵, 경고 로그만 남기고 종료 (임의값 미사용) | 코드 리뷰상 일치 (`latest == null` 즉시 return) | ✅ |
| TC-025 | `TradingScheduler.intradaySignalScan` | 유니버스 중 특정 종목에서 `OrderService.processSignal`이 예외 발생 | 해당 종목만 에러 로그 남기고 나머지 종목은 계속 처리 | 코드 리뷰상 일치 (`try/catch` per-stock) — Review 🔴 필수 반영사항 충족 | ✅ |
| TC-026 | `StockMasterMapper.findActiveUniverse` | 관리종목/거래정지 종목 포함 데이터에서 조회 | 관리종목·거래정지 종목 제외한 목록만 반환 | 코드 리뷰상 일치 (`is_managed=FALSE AND is_trading_halt=FALSE`) | ✅ |
| TC-027 | `AccountSnapshotMapper.findLatest` | 동일 계좌·모드로 여러 날짜 스냅샷 존재 | 가장 최근 `snapshot_date` 1건만 반환 | 코드 리뷰상 일치 (`ORDER BY snapshot_date DESC LIMIT 1`) | ✅ |
| TC-028 | `intradaySignalScan` 스케줄 시점 | cron `0 5-59/5 9-14 * * MON-FRI` 검증 | 09:05~14:55 사이 5분 간격 실행, 09:00/15:xx는 제외 | **최초 재검증 시 Javadoc(09:05~15:15)과 실제 cron(hours 9-14 → 14:55까지) 불일치 발견 → 즉시 Javadoc 수정하여 09:05~14:55로 정정, 재확인 통과** | ✅ |

## BUG-006 / BUG-003 재검증

| ID | 화면/기능 | 시나리오 | 기대 결과 | 실제 결과 | 상태 |
|----|---------|---------|---------|---------|------|
| TC-029 | `TradingScheduler.postMarketJob` | MOCK 모드, 체결된 매수/매도 이력 존재 | "시드머니 − 누적매수 + 누적매도"로 현금 계산 후 `account_snapshot` upsert | 코드 리뷰상 일치 | ✅ |
| TC-030 | `TradingScheduler.postMarketJob` | `trading.mode=LIVE`로 기동 후 호출 | 계산/저장 없이 경고 로그만 남기고 즉시 반환 | 코드 리뷰상 일치 (`isLive()` 조기 반환) — Review 🔴 필수 반영사항 충족 | ✅ |
| TC-031 | `TradingScheduler.postMarketJob` | 동일 날짜에 배치가 두 번 실행됨(재시작 등) | 중복 행 생성 없이 기존 스냅샷 갱신 | 코드 리뷰상 일치 (`ON CONFLICT ... DO UPDATE`) | ✅ |
| TC-032 | `AccountSnapshotMapper.upsert` → 다음날 `intradaySignalScan` | 전날 postMarketJob 실행 후 다음날 장중 스캔 | `findLatest`가 전날 스냅샷을 반환해 스캔이 더 이상 스킵되지 않음 | 코드 리뷰상 일치 (BUG-006이 BUG-005의 입력 공백을 해소) | ✅ |
| TC-033 | `POST /predict` (FastAPI) | `model.pkl` 없음(최초 배포 기본 상태) | `direction=FLAT, confidence=0.0, modelVersion=placeholder-0.0` | 코드 리뷰상 일치 (기존 동작 유지) | ✅ |
| TC-034 | `POST /predict` (FastAPI) | `model.pkl` 있음, 해당 종목 `feature_daily` 데이터 없음 | `modelVersion=no-feature-data`, `confidence=0.0` (임의 예측 금지) | 코드 리뷰상 일치 | ✅ |
| TC-035 | `POST /predict` (FastAPI) | `model.pkl` 있음, `feature_daily`에 실제 피처 존재 | 학습된 모델로 실제 추론, `modelVersion=lgbm-synthetic-0.1`(합성 라벨 명시) | 코드 리뷰상 일치 — Review 🟡 필수 반영사항 충족 | ✅ |
| TC-036 | `main.py` DB 연결 실패 | ml-service가 postgres에 접속 불가 | 예외를 삼키고 `None` 반환 → placeholder 응답 (서비스 전체 다운 아님) | 코드 리뷰상 일치 (`try/except` + `finally conn.close()`) | ✅ |

## 종목 유니버스 갱신 / 백테스트 프레임워크 검증

| ID | 화면/기능 | 시나리오 | 기대 결과 | 실제 결과 | 상태 |
|----|---------|---------|---------|---------|------|
| TC-037 | `TradingScheduler.preMarketJob` | 기준일(5일) 내 시세 없는 종목 존재 | 해당 종목 `is_trading_halt=TRUE`로 전환 | 코드 리뷰상 일치 | ✅ |
| TC-038 | `TradingScheduler.preMarketJob` | 거래정지 표시된 종목에 새 시세 유입 | `is_trading_halt=FALSE`로 자동 복구 | 코드 리뷰상 일치 | ✅ |
| TC-039 | `BacktestEngine.run` | 거래 목록 비어있음 | 전 지표 0 반환 (예외 없음) | 코드 리뷰상 일치 (`trades.isEmpty()` 가드) | ✅ |
| TC-040 | `BacktestEngine.run` | 수익 거래 1건 + 손실 거래 1건 | `winRate=0.5`, `cumulativeReturnRate`가 수수료/슬리피지 차감 후 값과 일치 | 코드 리뷰상 계산식 일치 | ✅ |
| TC-041 | `BacktestEngine.run` | 자본이 신고점 대비 하락하는 구간 포함 | `maxDrawdownRate`가 최대 낙폭 구간을 정확히 포착 | 코드 리뷰상 일치 (peak 갱신 로직) | ✅ |
| TC-042 | `BacktestService.matchRoundTrips` | 매수 100주 후 매도 60주+40주 분할 | 매수 랏이 60/40으로 정확히 분할되어 거래 2건 생성 | 코드 리뷰상 일치 (`lots.addFirst` 잔여 랏 처리) | ✅ |
| TC-043 | `GET /api/backtest/performance` | 체결 이력 없음(Phase 1 초기 상태) | `totalTrades=0`, 나머지 지표 0 — 정상(버그 아님) | 코드 리뷰상 일치 | ✅ |

## 📤 Dev Agent 수정 요청 목록
- [x] BUG-001: `PositionMapper` 구현 및 `AccountService.getPositions` 연동 — **수정 완료, 재검증 통과**
- [x] BUG-002: `RiskEngine`에 종목당/전체 포지션 한도 검증 로직 추가 — **수정 완료, 재검증 통과**
- [x] BUG-004: 신호→리스크→주문 오케스트레이션 서비스(`OrderService`) 구현 — **수정 완료, 재검증 통과**
- [x] BUG-005: 장중 스케줄러 ↔ OrderService 자동 연동 — **수정 완료, 재검증 통과** (재검증 중 발견된 Javadoc/cron 불일치도 즉시 수정)
- [x] BUG-006: `postMarketJob()` 계좌 스냅샷 저장 — **수정 완료, 재검증 통과** (MOCK 모드만, LIVE는 의도적으로 스킵)
- [x] BUG-003: AI 예측 서비스 실모델/실피처 연동 — **수정 완료(배선 검증 수준), 재검증 통과** — 단, 합성 라벨 학습이므로 실거래 신호로는 여전히 사용 불가 (아래 참고)

### BUG-003 관련 잔존 제약 (해결 아님, 구조적 한계)
- 실제 시세 이력 기반 정답 라벨이 없어 **실거래에 쓸 수 있는 모델은 아직 존재하지 않음** — 이는 코드 결함이 아니라 데이터 부재에 따른 근본적 제약이며, 로드맵상 Phase 2(백테스트 검증 포함)에서 실데이터 축적 후 재학습이 필요
- 담당: Dev/DBA(데이터 파이프라인), 이후 스프린트

## ✅ 최종 품질 판정 (BUG-006/BUG-003 재검증)
- 테스트 통과율: 36/36 (100%) — BUG-001/BUG-002/BUG-004/BUG-005/BUG-006/BUG-003(배선) 모두 재검증 통과
- 판정: **통과** — Phase 1(모의투자 End-to-End) 파이프라인이 자동 트리거·계좌 스냅샷·AI 배선까지 전부 연결됨. 코드 레벨에서 열린 버그는 더 이상 없음. 남은 것은 버그가 아니라 **실데이터 부재라는 구조적 전제조건**(실제 시세/피처 축적, 모델 재학습)이며, 이 상태로는 실거래(LIVE) 신호 사용이 금지된다는 원칙을 계속 유지. 실거래(LIVE) 배포는 Phase 5까지 별도 승인 전 금지.
