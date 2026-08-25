package com.jdwork.autotrading.risk;

import com.jdwork.autotrading.order.domain.OrderLog;
import com.jdwork.autotrading.order.mapper.OrderLogMapper;
import com.jdwork.autotrading.risk.domain.RiskEvent;
import com.jdwork.autotrading.strategy.domain.StrategySignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 리스크 관리 엔진 — 전략/신호 로직과 완전히 독립된 최종 방어선 (설계 §6).
 * Risk management engine — the final safety net, fully independent from strategy/signal logic (design doc §6).
 *
 * 전략 엔진에 버그가 있어도 이 엔진이 손절/한도/이상매매를 걸러낸다. 절대 strategy 패키지에 의존하지 않는다.
 * Even if the strategy engine has a bug, this engine filters stop-loss/limits/anomalies.
 * MUST NOT depend on the strategy package.
 */
@Service
public class RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BigDecimal dailyLossLimitRate;
    private final BigDecimal maxPositionRatioPerStock;
    private final BigDecimal minCashRatio;
    private final int cooldownTradingDays;
    private final int maxDailyTrades;
    private final OrderLogMapper orderLogMapper;

    /** 수동 긴급정지(Kill Switch) 상태 — 대시보드 버튼으로 즉시 true 전환 (설계 §6, §9.1). */
    /** Manual kill switch state — flipped to true instantly via the dashboard button (design doc §6, §9.1). */
    private final AtomicBoolean emergencyStopped = new AtomicBoolean(false);

    public RiskEngine(
            @Value("${trading.risk.daily-loss-limit-rate}") BigDecimal dailyLossLimitRate,
            @Value("${trading.risk.max-position-ratio-per-stock}") BigDecimal maxPositionRatioPerStock,
            @Value("${trading.risk.min-cash-ratio}") BigDecimal minCashRatio,
            @Value("${trading.risk.cooldown-trading-days}") int cooldownTradingDays,
            @Value("${trading.risk.max-daily-trades}") int maxDailyTrades,
            OrderLogMapper orderLogMapper) {
        this.dailyLossLimitRate = dailyLossLimitRate;
        this.maxPositionRatioPerStock = maxPositionRatioPerStock;
        this.minCashRatio = minCashRatio;
        this.cooldownTradingDays = cooldownTradingDays;
        this.maxDailyTrades = maxDailyTrades;
        this.orderLogMapper = orderLogMapper;
    }

    /**
     * 신호가 실제 주문으로 이어져도 되는지 최종 검증한다.
     * Final validation of whether a signal may proceed to an actual order.
     *
     * @param signal            검증 대상 신호 (signal to validate)
     * @param currentDailyLossRate 당일 실현 손실률 (음수면 손실) (today's realized loss rate; negative means a loss)
     * @return 검증 결과 (validation result)
     */
    public RiskCheckResult validate(StrategySignal signal, BigDecimal currentDailyLossRate) {
        if (emergencyStopped.get()) {
            return RiskCheckResult.rejected("긴급정지 상태 — 모든 신규 주문 차단 (emergency stop active — all new orders blocked)");
        }
        if (currentDailyLossRate.negate().compareTo(dailyLossLimitRate) >= 0) {
            // 일일 최대 손실 한도 도달 → Circuit Breaker 발동 (daily loss limit reached → circuit breaker trips)
            return RiskCheckResult.rejected("일일 최대 손실 한도 도달 (daily max loss limit reached)");
        }
        // 종목당 포지션 한도/최소 현금 비율은 정확한 수량·가격이 필요하므로 주문 생성 직전 validateOrder()에서 검증한다.
        // Per-stock position limit / min cash ratio need an exact quantity & price, so they're checked in
        // validateOrder() right before order creation instead of here.
        return RiskCheckResult.approve();
    }

    /**
     * 주문 생성 직전 최종 검증 — 종목당 포지션 한도, 최소 현금 보유 비율을 확인한다 (설계 §6, BUG-002 수정).
     * Final validation right before order creation — checks per-stock position limit and min cash ratio
     * (design doc §6, BUG-002 fix).
     *
     * BUY 주문에만 적용한다. SELL은 보유 비중을 줄이는 방향이라 포지션/현금 한도 취지에 위배되지 않는다.
     * Applies to BUY orders only — SELL reduces exposure, so it never violates these limits.
     */
    public RiskCheckResult validateOrder(OrderLog order, AccountRiskContext context) {
        if (emergencyStopped.get()) {
            return RiskCheckResult.rejected("긴급정지 상태 — 모든 신규 주문 차단 (emergency stop active — all new orders blocked)");
        }
        if (order.getOrderType() != OrderLog.OrderType.BUY) {
            return RiskCheckResult.approve();
        }
        if (context.totalAccountValue().signum() <= 0) {
            // 계좌 평가금액을 알 수 없으면 비율 계산이 무의미하므로 안전하게 차단한다.
            // Cannot compute ratios without a known account value — fail safe by rejecting.
            return RiskCheckResult.rejected("계좌 평가금액 조회 실패 (account valuation unavailable)");
        }

        BigDecimal orderValue = order.getOrderPrice().multiply(BigDecimal.valueOf(order.getQuantity()));

        BigDecimal projectedPositionRatio = context.targetPositionValue().add(orderValue)
                .divide(context.totalAccountValue(), 6, RoundingMode.HALF_UP);
        if (projectedPositionRatio.compareTo(maxPositionRatioPerStock) > 0) {
            return RiskCheckResult.rejected("종목당 포지션 한도 초과 (per-stock position limit exceeded)");
        }

        BigDecimal projectedCashRatio = context.cashBalance().subtract(orderValue)
                .divide(context.totalAccountValue(), 6, RoundingMode.HALF_UP);
        if (projectedCashRatio.compareTo(minCashRatio) < 0) {
            return RiskCheckResult.rejected("최소 현금 보유 비율 미달 (minimum cash ratio violated)");
        }

        return RiskCheckResult.approve();
    }

    /**
     * 과다매매(잦은 재매매) 방지 검증 — 설계 §6 "이상 매매 감지" 항목의 일부 (증권사 수수료 부담 완화 목적).
     * Overtrading-prevention check — part of the design doc §6 "anomaly detection" item (aimed at
     * curbing brokerage fee drag from too-frequent buy/sell cycles).
     *
     * 1) 종목별 쿨다운: 해당 종목에 최근 주문이 있었다면 설정된 거래일수가 지나기 전까지 재매매 차단.
     *    ⚠️ "거래일"은 달력일로 근사한다(주말/공휴일 미반영) — 정밀한 영업일 계산이 필요하면 추후 개선.
     * 2) 계좌 전체 일일 최대 거래 횟수: 하루 동안 발생한 총 주문 건수가 한도에 도달하면 신규 주문 차단.
     *
     * 1) Per-stock cooldown: blocks re-trading the same stock until the configured number of trading
     *    days has passed since its last order. ⚠️ "Trading days" is approximated as calendar days
     *    (weekends/holidays not excluded) — refine later if precise business-day math is needed.
     * 2) Account-wide daily trade cap: blocks new orders once today's total order count hits the limit.
     */
    public RiskCheckResult checkOvertrading(String stockCode, String tradingMode) {
        OffsetDateTime lastOrderAt = orderLogMapper.findLastOrderTime(stockCode, tradingMode);
        if (lastOrderAt != null) {
            long daysSinceLastOrder = ChronoUnit.DAYS.between(lastOrderAt.toLocalDate(), LocalDate.now(KST));
            if (daysSinceLastOrder < cooldownTradingDays) {
                return RiskCheckResult.rejected("종목 재매매 최소 보유기간 미충족 — 최근 주문 후 " + daysSinceLastOrder
                        + "일 경과(기준 " + cooldownTradingDays + "일) (per-stock cooldown active)");
            }
        }

        OffsetDateTime startOfToday = LocalDate.now(KST).atStartOfDay(KST).toOffsetDateTime();
        int todaysTradeCount = orderLogMapper.countOrdersSince(tradingMode, startOfToday);
        if (todaysTradeCount >= maxDailyTrades) {
            return RiskCheckResult.rejected("일일 최대 거래 횟수 초과(" + todaysTradeCount + "/" + maxDailyTrades
                    + ") (daily trade limit exceeded)");
        }

        return RiskCheckResult.approve();
    }

    /** 대시보드 긴급정지 버튼에서 호출 — 즉시 모든 신규 주문을 차단한다 (called from the dashboard emergency-stop button). */
    public RiskEvent triggerEmergencyStop(String accountId, String tradingMode) {
        emergencyStopped.set(true);
        log.warn("긴급정지 발동: account={}, mode={} (emergency stop triggered)", accountId, tradingMode);
        return new RiskEvent(RiskEvent.EventType.MANUAL_KILL_SWITCH, accountId, tradingMode, "신규 주문 전체 중단");
    }

    public boolean isEmergencyStopped() {
        return emergencyStopped.get();
    }

    public record RiskCheckResult(boolean approved, String rejectReason) {
        // 정적 팩토리 메서드명은 레코드 컴포넌트 접근자(approved())와 겹치면 컴파일 에러가 나므로 approve()로 명명
        // Named approve() (not approved()) — colliding with the record component accessor approved() fails to compile
        static RiskCheckResult approve() { return new RiskCheckResult(true, null); }
        static RiskCheckResult rejected(String reason) { return new RiskCheckResult(false, reason); }
    }
}
