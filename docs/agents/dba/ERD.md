# 🗄️ ERD 설명 — 주식 자동매매 시스템

> 최종 업데이트: 2026-08-24

## 1. 엔티티 분석

- **주요 엔티티**: 종목(stock_master), 시세(price_history), 피처(feature_daily), AI예측(prediction_log),
  매매신호(strategy_signal), 주문(order_log), 포지션(position), 리스크이벤트(risk_log), 계좌스냅샷(account_snapshot)
- **관계 유형**: 대부분 1:N (종목 1개가 시세/피처/예측/신호/주문/포지션 다수를 가짐), 신호→주문은 1:0..1 (신호가 리스크 검증에서 기각되면 주문 없음)

## 2. ERD 관계도

```
stock_master (종목마스터)
  ├──1:N──> price_history      (일봉/분봉 시세)
  ├──1:N──> feature_daily      (기술지표 피처)
  ├──1:N──> prediction_log     (AI 예측 결과)
  ├──1:N──> strategy_signal    (매매 신호)
  ├──1:N──> order_log          (주문 이력)
  └──1:N──> position           (현재 포지션, 계좌+모드+종목 단위)

strategy_signal
  └──1:0..1──> order_log       (신호가 리스크 검증 통과 시에만 주문 생성)

order_log
  └──N:1──> risk_log           (주문이 리스크 이벤트에 의해 차단/취소된 경우 참조, nullable)

account_snapshot (계좌ID + 일자 단위, 종목 무관 — 계좌 전체 스냅샷)
```

## 3. 모드(모의/실전) 분리 원칙

`order_log`, `position`, `account_snapshot`은 반드시 `trading_mode`(MOCK/LIVE) 컬럼을 가지며,
동일 계좌ID라도 모드가 다르면 별개 데이터로 취급한다. 조회/집계 시 `trading_mode` 필터를 누락하면
모의투자와 실거래 데이터가 섞이는 치명적 오류로 이어지므로, 애플리케이션 레벨에서도 이중 검증한다
(Review Agent 검토 항목).

## 4. 파티셔닝 대상

`price_history`, `feature_daily`, `prediction_log`는 데이터가 매일 대량 적재되므로 `RANGE` 파티셔닝(월 단위, `일자`/`예측일` 기준)을 적용한다. 상세는 [schema.sql](schema.sql) 하단 주석 참고.

## 📤 Designer/Dev에게 전달할 사항

- **Designer**: 리스크 상태 패널은 `risk_log`(이상매매/한도초과 이벤트) + `account_snapshot`(당일 손익률)을 조합해 표시. 포지션 카드는 `position` 테이블의 `trading_mode`로 모의/실전 배지를 구분할 것.
- **Dev**: `OrderExecutor` 구현체는 주문 생성 시 반드시 `trading_mode`를 함께 기록. `risk_log.blocked_order_id`는 nullable FK이며, 리스크 엔진이 주문을 차단한 경우에만 채워짐.
- FK 제약: `deleted_at IS NULL` 조건은 애플리케이션(MyBatis) 쿼리에서 항상 포함 — DB 레벨 소프트 삭제이므로 FK 참조 무결성은 유지되나 "논리적으로 삭제된 종목"을 조회에서 제외하는 책임은 애플리케이션에 있음.
