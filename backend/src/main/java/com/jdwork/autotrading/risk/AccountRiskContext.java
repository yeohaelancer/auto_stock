package com.jdwork.autotrading.risk;

import java.math.BigDecimal;

/**
 * RiskEngine의 주문 단위 검증(종목당 포지션 한도, 최소 현금 비율, 공매도 차단)에 필요한 계좌 스냅샷.
 * Account snapshot needed for RiskEngine's per-order checks (per-stock position limit, min cash ratio,
 * blocking short-sells).
 *
 * @param totalAccountValue  계좌 평가금액(현금 + 보유 포지션 평가액) (cash + position valuation)
 * @param cashBalance        현재 현금 잔고 (current cash balance)
 * @param targetPositionValue 검증 대상 종목의 현재 포지션 평가액 (current valuation of the target stock's position)
 * @param targetPositionQuantity 검증 대상 종목의 현재 보유 수량 — SELL 주문이 이 수량을 넘지 못하도록 막는 데 사용
 *                               (설계 §6, 실운영 중 발견: 이게 없어서 보유하지 않은 종목도 SELL이 그대로 체결됐음)
 *                               Current held quantity of the target stock — used to cap SELL orders at
 *                               this amount (design doc §6; found in real operation: without this, SELL
 *                               orders filled even for stocks with zero holdings)
 */
public record AccountRiskContext(
        BigDecimal totalAccountValue,
        BigDecimal cashBalance,
        BigDecimal targetPositionValue,
        int targetPositionQuantity
) {
}
