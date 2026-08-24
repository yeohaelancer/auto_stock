package com.jdwork.autotrading.risk;

import java.math.BigDecimal;

/**
 * RiskEngine의 주문 단위 검증(종목당 포지션 한도, 최소 현금 비율)에 필요한 계좌 스냅샷.
 * Account snapshot needed for RiskEngine's per-order checks (per-stock position limit, min cash ratio).
 *
 * @param totalAccountValue  계좌 평가금액(현금 + 보유 포지션 평가액) (cash + position valuation)
 * @param cashBalance        현재 현금 잔고 (current cash balance)
 * @param targetPositionValue 검증 대상 종목의 현재 포지션 평가액 (current valuation of the target stock's position)
 */
public record AccountRiskContext(
        BigDecimal totalAccountValue,
        BigDecimal cashBalance,
        BigDecimal targetPositionValue
) {
}
