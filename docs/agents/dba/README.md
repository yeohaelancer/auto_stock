# 🗄️ DBA Agent 산출물 — 주식 자동매매 시스템

> 최종 업데이트: 2026-08-24

## 1. 엔티티 분석
- 주요 엔티티: `stock_master`, `price_history`, `feature_daily`, `prediction_log`, `strategy_signal`,
  `order_log`, `position`, `risk_log`, `account_snapshot` (총 9개 테이블)
- 관계 유형: 1:N 위주 (종목 마스터를 중심으로 시세/피처/예측/신호/주문/포지션이 파생), 신호→주문은 1:0..1

상세 관계도는 [ERD.md](ERD.md) 참고.

## 2. DDL 스크립트
전체 DDL은 [schema.sql](schema.sql) 참고. 요약:
- PK: `stock_master`는 자연키(종목코드), 그 외 대부분 `BIGINT IDENTITY` 또는 `UUID`(`strategy_signal`, `order_log`)
- 모든 테이블에 `created_at`, `updated_at`(변경 이력이 있는 테이블만), `deleted_at`(soft delete) 포함
- 3NF 정규화 적용 — 피처/예측/신호/주문을 분리하여 각 단계의 이력을 독립적으로 추적 가능

## 3. 인덱스 전략
| 테이블 | 인덱스 | 이유 |
|---|---|---|
| `stock_master` | `(market_type)`, `(is_managed, is_trading_halt)` | 종목 유니버스 필터링 (§5.3) 조회 최적화 |
| `price_history` | `(stock_code, interval_type, trade_datetime DESC)` | 종목별 최신 시세 조회 |
| `feature_daily` | UNIQUE `(stock_code, base_date, feature_version)` | 동일 버전 피처 중복 적재 방지 |
| `prediction_log` | `(stock_code, predict_date DESC)` | AI 신호 피드 최신순 조회 |
| `strategy_signal` | `(stock_code, generated_at DESC)`, `(status)` | 신호 타임라인, 대기중 신호 조회 |
| `order_log` | `(stock_code, trading_mode, created_at DESC)`, `(execution_status)` | 주문 로그 화면, 미체결 주문 감시 |
| `position` | UNIQUE `(account_id, trading_mode, stock_code)` | 계좌·모드·종목당 포지션 1건 보장 |
| `account_snapshot` | UNIQUE `(account_id, trading_mode, snapshot_date)` | 일별 스냅샷 중복 방지 |

## 4. 최적화 권고
- **파티셔닝**: `price_history`, `feature_daily`, `prediction_log`는 월 단위 RANGE 파티셔닝 적용 (데이터 급증 예상 테이블). 파티션 자동 생성은 운영 배치(스케줄러)에서 익월분을 미리 생성하도록 Dev/DevOps에 전달.
- **캐싱 레이어**: 장중 실시간 조회가 잦은 `position`, `account_snapshot`(당일)은 Redis 캐싱 검토 (DevOps 산출물의 redis 컨테이너와 연계). 시세 데이터는 WebSocket 실시간 스트림이 우선이므로 DB 캐싱보다 애플리케이션 메모리 캐시 권장.
- pgvector는 1차 범위에서 불필요 — 2단계(유사 패턴 검색) 도입 시 `feature_daily`에 임베딩 컬럼 추가 검토.

## 5. 준수 원칙 체크
- ✅ 3NF 정규화 기본 적용
- ✅ PK: UUID(`strategy_signal`, `order_log`) 또는 BIGINT IDENTITY
- ✅ 모든 테이블 `created_at`/`deleted_at`(soft delete) 포함
- ✅ 민감 정보(앱키/시크릿/계좌번호)는 이 스키마에 포함하지 않음 — Credential은 DB가 아닌 Vault/환경변수로 관리 (DevOps 산출물 참고), 계좌번호는 `account_id`로만 참조하고 원본은 별도 보안 저장소 권장

## 📤 Designer/Dev에게 전달할 사항
- **모드 분리 필수**: `order_log`, `position`, `account_snapshot`의 `trading_mode`(MOCK/LIVE) 필터링을 애플리케이션 전 계층(쿼리·화면·리스크 엔진)에서 누락 없이 적용할 것 — 모의/실전 데이터 혼동은 이 시스템에서 가장 위험한 버그 클래스.
- 리스크 상태 패널(Designer)은 `risk_log` + `account_snapshot.daily_pnl_rate` 조합으로 구성.
- `order_log.blocked_by_risk_id`가 not null이면 리스크 엔진에 의해 차단된 주문이므로, 대시보드 주문 로그에서 "차단됨" 상태로 별도 표시 필요.
- FK 제약상 `stock_master` 삭제(soft delete) 시에도 과거 이력 테이블 참조는 유지됨 — 조회 시 `deleted_at IS NULL` 조건은 애플리케이션 책임.

## 📌 변경 이력 (2026-08-24, BUG-004 오케스트레이션 구현 단계)
이번 요청(신호→리스크→주문 오케스트레이션 서비스 구현)은 **스키마 변경이 필요 없음**을 확인했습니다.
`risk_log` 테이블은 이미 `blocked_order_id` 관계를 뒷받침하는 구조로 설계되어 있었고, Dev가 구현하는 `RiskEventMapper`는 기존 `risk_log` 테이블에 그대로 INSERT합니다. DDL 재작업 없이 다음 단계(Review)로 진행합니다.

## 📌 변경 이력 (2026-08-24, BUG-006/BUG-003 일괄 수정 단계)
BUG-006(계좌 스냅샷 저장), BUG-003(AI 예측 실연동)은 모두 **기존 스키마(account_snapshot, feature_daily)를 그대로 사용**하며 DDL 변경이 필요 없음을 확인했습니다. `account_snapshot`의 `UNIQUE(account_id, trading_mode, snapshot_date)` 제약은 이미 upsert(ON CONFLICT) 처리를 뒷받침하도록 설계되어 있어 Dev가 그대로 활용합니다.

## 📌 변경 이력 (2026-08-24, BUG-005 스케줄러 자동 연동 단계)
장중 스케줄러가 종목 유니버스(`stock_master`)와 최근 계좌 스냅샷(`account_snapshot`)을 조회해야 하므로, 두 테이블에 대한 **신규 조회 전용 매퍼**가 필요합니다. 두 테이블 모두 기존 DDL에 이미 존재하므로 **스키마 변경은 없음**. `stock_master.is_managed`/`is_trading_halt`/`deleted_at`, `account_snapshot`의 `(account_id, trading_mode, snapshot_date DESC)` 조합을 그대로 활용합니다.
