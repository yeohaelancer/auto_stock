# 🧐 Review Agent 검토 결과

> 최종 업데이트: 2026-08-24

## 검토 대상
- DBA 산출물: `docs/agents/dba/` (ERD.md, schema.sql, README.md)
- Designer 산출물: `docs/agents/designer/` (design-system.md, components.md, screens/auto-trading-widget.md)

## 발견 이슈

| 심각도 | 항목 | 설명 | 권고 조치 |
|--------|------|------|----------|
| 🟡 중간 | 리스크 게이지 데이터 소스 | `RiskGauge` props(`currentLossRate`, `dailyLimitRate`)가 참조할 컬럼이 `account_snapshot.daily_pnl_rate`(일별, 배치성)뿐이며, 장중 실시간 손실률을 담는 컬럼이 스키마에 없음 | Dev 단계에서 장중 실시간 손실률은 애플리케이션 메모리(당일 체결 기준 계산)로 산출하고 `account_snapshot`은 일별 확정치로만 사용하도록 역할을 분리할 것. DBA 재작업은 불필요 |
| 🟡 중간 | 긴급정지(Kill Switch) 경로 불명확 | Designer 명세의 `EmergencyStopButton` → API 호출과, DBA `risk_log.event_type='MANUAL_KILL_SWITCH'` 기록 경로가 문서상으로만 연결되어 있고 실제 API 계약(엔드포인트/응답)이 정의되지 않음 | Dev 착수 시 `POST /api/risk/emergency-stop` 계약을 최우선 정의하고, 호출 즉시 `risk_log` insert + 모든 `PENDING` 주문 취소가 원자적으로 처리되도록 트랜잭션 설계 필수 |
| 🟢 낮음 | 디자인 토큰 실값 미정 | `design-system.md`의 컬러 토큰이 "기존 팔레트 기준 예시"로만 기술되고 실제 HEX 값/토큰 파일 경로가 없음 | 기존 JD WORK 디자인 시스템 토큰 파일 경로를 Designer 또는 사용자에게 확인 후 매핑 (기능 구현을 막는 이슈는 아니므로 Dev 진행에 지장 없음) |
| 🟢 낮음 | `order_log.blocked_by_risk_id` 표시 연동 | DBA README는 `blocked_by_risk_id` not null 시 "차단됨" 표시를 권고했고 Designer `OrderLogRow`도 `blocked` prop을 이미 반영함 — 정합성 확인됨, 이슈 아님 | 조치 불필요 (참고용으로 기재) |

## 개선 권고사항
1. 모의/실전(`trading_mode`) 필터링은 DBA·Designer 모두 명시했으나, Dev 구현 시 컨트롤러/서비스/쿼리 전 계층에서 통합 테스트로 검증할 것 (QA 단계 필수 항목으로 전달).
2. 리스크 엔진은 전략 엔진과 독립 모듈로 설계하라는 기획 문서 원칙이 DBA/Designer 산출물 모두에 반영되어 있음 — Dev 모듈 구조(`risk/` 패키지 분리)에서도 동일하게 지켜져야 함.

## 📤 Dev Agent 전달 주의사항
- **반드시 반영**: `trading_mode` 필터링 전 계층 적용, 긴급정지 API의 원자적 트랜잭션 처리, 장중 실시간 손실률과 `account_snapshot` 역할 분리
- **선택적 개선사항**: 디자인 토큰 실값 매핑(진행 중 확인 가능, 블로킹 아님)

## ✅ 검토 판정
- [x] 조건부 통과 (경미한 이슈 Dev 진행 중 수정) — 🔴 높음 등급 이슈 없음, 🟡 중간 이슈 2건은 Dev 단계에서 반영 조건으로 진행 승인

---

## 추가 검토 (2026-08-24) — BUG-004 오케스트레이션 서비스 착수 전 사전 점검

### 검토 대상
- 대상: `SignalEngine`(strategy) → `RiskEngine`(risk) → `OrderExecutor`(order)를 잇는 신규 `OrderService`
- DBA/Designer 변경 없음 확인 (각 README/components.md 변경 이력 참고)

### 발견 이슈
| 심각도 | 항목 | 설명 | 권고 조치 |
|--------|------|------|----------|
| 🟡 중간 | 시세 조회 미연동 상태에서의 안전성 | `KiwoomMarketClient` 실제 구현체가 없어 주문 가격을 알 수 없는 상태 — 오케스트레이션이 이 경우 임의 가격으로 대체하면 안 됨 | Dev는 시세 조회 실패/빈 값 시 반드시 신호 처리 자체를 스킵(매매 안 함)하도록 구현할 것 — 설계 §10 원칙 재확인 |
| 🟢 낮음 | 리스크 차단 이벤트의 감사 추적 | `order_log.blocked_by_risk_id`가 실제로 채워지려면 `risk_log` insert 후 생성된 ID를 참조해야 함 | `RiskEventMapper`에 `useGeneratedKeys`로 ID를 되돌려받아 `OrderLog.blockedByRiskId`에 설정할 것 |

### ✅ 검토 판정
- [x] 통과 (Dev 진행 승인) — 🔴 높음 등급 이슈 없음, 🟡 이슈 1건은 Dev 구현 시 반영 조건

---

## 추가 검토 (2026-08-24) — BUG-005 스케줄러 자동 연동 착수 전 사전 점검

### 검토 대상
- 대상: `TradingScheduler`가 `OrderService.processSignal`을 주기적으로 자동 호출하도록 하는 변경

### 발견 이슈
| 심각도 | 항목 | 설명 | 권고 조치 |
|--------|------|------|----------|
| 🔴 높음 | 종목 1개 실패가 전체 스캔을 중단시킬 위험 | 유니버스를 순회하며 `processSignal`을 호출할 때 특정 종목에서 예외가 발생하면 반복문이 중단되어 나머지 종목이 아예 처리되지 않을 수 있음 | 종목별 호출을 try/catch로 감싸 한 종목의 실패가 나머지 종목 처리를 막지 않도록 구현 필수 (긴급정지·Kill Switch와는 별개의 안정성 요건) |
| 🟡 중간 | 계좌 스냅샷 부재 시 동작 | 최초 배포 시 `account_snapshot`이 아직 없으면 현금 잔고/손실률을 알 수 없음 | 스냅샷이 없으면 해당 스캔 사이클을 스킵(매매 안 함)하고 경고 로그만 남기도록 구현 — 임의값 대체 금지 원칙(설계 §10) 재확인 |
| 🟢 낮음 | 스캔 주기 중 긴급정지 시점 | 스캔이 진행되는 도중 긴급정지가 눌리면 이미 시작된 사이클의 나머지 종목은 계속 처리될 수 있음 | `OrderService.processSignal` 내부에서 매 종목마다 `RiskEngine.validate`가 최신 상태를 다시 확인하므로 실질적 위험은 낮음 — 참고용으로만 기재, 블로킹 아님 |

## 📤 Dev Agent 전달 주의사항 (BUG-005)
- **반드시 반영**: 종목별 예외 격리(🔴), 계좌 스냅샷 부재 시 안전 스킵(🟡)

### ✅ 검토 판정
- [x] 통과 (Dev 진행 승인) — 🔴 이슈 1건은 Dev 구현 시 필수 반영 조건

---

## 추가 검토 (2026-08-24) — BUG-006(계좌 스냅샷 저장) / BUG-003(AI 예측 실연동) 착수 전 사전 점검

### 검토 대상
- BUG-006: `TradingScheduler.postMarketJob()`이 `account_snapshot`을 실제로 계산·저장하도록 하는 변경
- BUG-003: `ml-service`가 학습된 모델로 실제 추론하고 `feature_daily`를 조회하도록 하는 변경

### 발견 이슈
| 심각도 | 항목 | 설명 | 권고 조치 |
|--------|------|------|----------|
| 🔴 높음 | LIVE 모드 현금 잔고 조작 위험 | BUG-006 해결책으로 제시된 "초기 시드머니 − 누적 매수 + 누적 매도" 계산법은 시스템이 자금 흐름을 전부 통제하는 **MOCK 모드에서만 정당함**. LIVE 모드에 동일 로직을 적용하면 실제 계좌 잔고가 아닌 허구의 값을 실거래 손실 한도 계산에 사용하게 되어 리스크 엔진이 잘못된 판단을 할 수 있음 | Dev는 반드시 `TradingModeConfig.isLive()`로 분기하여, LIVE 모드에서는 스냅샷 저장을 스킵하고 경고 로그만 남길 것 (실거래 잔고 조회 API 연동 전까지) |
| 🟡 중간 | AI 모델 학습 라벨의 정직한 표기 | 합성(synthetic) 라벨로 학습한 모델을 `modelVersion`에 정직하게 반영하지 않으면 향후 이 값이 실거래 신호로 오인될 위험 | `modelVersion`에 "synthetic" 등 명확한 표식을 남기고, `feature_daily`에 데이터가 없는 종목은 예측 자체를 스킵(placeholder 반환)할 것 |
| 🟢 낮음 | ml-service의 DB 직접 접근 | FastAPI가 PostgreSQL에 직접 연결하는 것은 설계 §4.4의 "Spring Boot와 분리된 별도 프로세스" 원칙과 상충하지 않음(읽기 전용) — 다만 자격증명 관리가 backend와 별도로 필요함을 DevOps에 전달 | DevOps 산출물에 ml-service용 DB 자격증명 관리 항목 반영 |

## 📤 Dev Agent 전달 주의사항
- **반드시 반영**: LIVE 모드에서 계좌 스냅샷 fabrication 금지(🔴), 합성 모델임을 `modelVersion`에 명시(🟡)

### ✅ 검토 판정
- [x] 통과 (Dev 진행 승인) — 🔴 이슈 1건은 Dev 구현 시 필수 반영 조건
