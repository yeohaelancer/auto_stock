package com.jdwork.autotrading.order;

import com.jdwork.autotrading.account.AccountService;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.config.TradingModeConfig;
import com.jdwork.autotrading.market.KiwoomMarketClient;
import com.jdwork.autotrading.market.dto.PriceBar;
import com.jdwork.autotrading.order.domain.OrderLog;
import com.jdwork.autotrading.order.mapper.OrderLogMapper;
import com.jdwork.autotrading.risk.AccountRiskContext;
import com.jdwork.autotrading.risk.RiskEngine;
import com.jdwork.autotrading.risk.domain.RiskEvent;
import com.jdwork.autotrading.risk.mapper.RiskEventMapper;
import com.jdwork.autotrading.strategy.SignalEngine;
import com.jdwork.autotrading.strategy.domain.StrategySignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 신호(Signal) → 리스크 검증(Risk) → 주문 실행(Order)을 잇는 오케스트레이션 서비스 (설계 §5.1, §8; BUG-004 수정).
 * Orchestration service tying signal generation → risk validation → order execution together
 * (design doc §5.1, §8; BUG-004 fix).
 *
 * 리스크 검증은 이 클래스가 호출만 할 뿐 판단 로직은 여전히 RiskEngine에만 있다 — 오케스트레이션이
 * 스스로 리스크를 판단하지 않는다(설계 §6 "완전히 독립된 모듈" 원칙 유지).
 * This class only *calls* risk validation — the decision logic still lives solely in RiskEngine,
 * preserving the "fully independent module" principle from design doc §6.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 고정비율 포지션 사이징 — 계좌 평가금액 대비 종목당 최대 비중을 그대로 목표 매수 비중으로 사용 (설계 §5.2). */
    /** Fixed-ratio position sizing — reuses the per-stock max ratio as the target buy ratio (design doc §5.2). */
    private final BigDecimal targetPositionRatio;

    private final SignalEngine signalEngine;
    private final RiskEngine riskEngine;
    private final AccountService accountService;
    private final KiwoomMarketClient marketClient;
    private final OrderExecutor orderExecutor;
    private final OrderLogMapper orderLogMapper;
    private final RiskEventMapper riskEventMapper;
    private final TradingModeConfig tradingModeConfig;
    private final KiwoomApiProperties kiwoomApiProperties;

    public OrderService(SignalEngine signalEngine,
                         RiskEngine riskEngine,
                         AccountService accountService,
                         KiwoomMarketClient marketClient,
                         OrderExecutor orderExecutor,
                         OrderLogMapper orderLogMapper,
                         RiskEventMapper riskEventMapper,
                         TradingModeConfig tradingModeConfig,
                         KiwoomApiProperties kiwoomApiProperties,
                         @org.springframework.beans.factory.annotation.Value("${trading.risk.max-position-ratio-per-stock}")
                         BigDecimal targetPositionRatio) {
        this.signalEngine = signalEngine;
        this.riskEngine = riskEngine;
        this.accountService = accountService;
        this.marketClient = marketClient;
        this.orderExecutor = orderExecutor;
        this.orderLogMapper = orderLogMapper;
        this.riskEventMapper = riskEventMapper;
        this.tradingModeConfig = tradingModeConfig;
        this.kiwoomApiProperties = kiwoomApiProperties;
        this.targetPositionRatio = targetPositionRatio;
    }

    /**
     * 지정 종목에 대해 신호 생성부터 주문 실행까지 전체 파이프라인을 1회 수행한다.
     * Runs the full pipeline once for the given stock: signal generation through order execution.
     *
     * @param cashBalance          현재 현금 잔고 — 실시간 잔고 조회 API 연동 전까지 호출부에서 전달 (TODO, 설계 §8 후속 과제)
     *                             Current cash balance — passed by the caller until a live balance API is wired (TODO)
     * @param currentDailyLossRate 당일 실현 손실률 (음수면 손실) (today's realized loss rate; negative means a loss)
     * @return 생성된 주문 이력 (신호 없음/HOLD/시세 미확보/거부 시 empty)
     *         The resulting order log (empty if no signal / HOLD / no price available / rejected before order creation)
     */
    public Optional<OrderLog> processSignal(String stockCode, BigDecimal cashBalance, BigDecimal currentDailyLossRate) {
        Optional<StrategySignal> maybeSignal = signalEngine.generateSignal(stockCode);
        if (maybeSignal.isEmpty()) {
            return Optional.empty(); // 예측 실패/신뢰도 미달 — SignalEngine이 이미 스킵 처리 (already skipped by SignalEngine)
        }

        StrategySignal signal = maybeSignal.get();
        if (signal.getSignalType() == StrategySignal.SignalType.HOLD) {
            return Optional.empty();
        }

        String accountId = kiwoomApiProperties.getAccountNo();
        String tradingMode = tradingModeConfig.getMode().name();

        // 1단계: 신호 단위 사전 검증 (긴급정지, 일일 손실 한도) — Circuit Breaker 역할
        // Step 1: signal-level pre-check (emergency stop, daily loss limit) — acts as the circuit breaker
        RiskEngine.RiskCheckResult signalCheck = riskEngine.validate(signal, currentDailyLossRate);
        if (!signalCheck.approved()) {
            log.info("신호 단계에서 리스크 거부: {} - {} (rejected at signal stage: {} - {})",
                    stockCode, signalCheck.rejectReason(), stockCode, signalCheck.rejectReason());
            return Optional.empty(); // 리스크 사전 차단 시 주문 자체를 만들지 않음 (never create an order once pre-blocked)
        }

        // 1-2단계: 과다매매 방지 검증(쿨다운/일일 거래 한도) — 사용자 요청(수수료 부담 완화) 반영
        // Step 1-2: overtrading-prevention check (cooldown/daily trade cap) — per user request to curb fee drag
        RiskEngine.RiskCheckResult overtradingCheck = riskEngine.checkOvertrading(stockCode, tradingMode);
        if (!overtradingCheck.approved()) {
            log.info("과다매매 방지로 신호 거부: {} - {} (rejected by overtrading guard: {} - {})",
                    stockCode, overtradingCheck.rejectReason(), stockCode, overtradingCheck.rejectReason());
            return Optional.empty();
        }

        // 2단계: 시세 조회 — 실패/미연동 시 절대 임의 가격으로 대체하지 않고 매매를 스킵 (설계 §10, Review 지적사항)
        // Step 2: price lookup — never substitute a fabricated price on failure; skip the trade instead (design doc §10)
        BigDecimal price = latestClosePrice(stockCode);
        if (price == null) {
            log.warn("시세 미확보로 {} 종목 주문을 스킵합니다 (skipping order for {} — no price available)", stockCode, stockCode);
            return Optional.empty();
        }

        // 3단계: 고정비율 포지션 사이징 (설계 §5.2)
        // Step 3: fixed-ratio position sizing (design doc §5.2)
        AccountRiskContext context = accountService.getRiskContext(accountId, tradingMode, cashBalance, stockCode);
        int quantity = calculateQuantity(context.totalAccountValue(), price);
        if (quantity <= 0) {
            return Optional.empty(); // 매수 여력 없음 (insufficient buying power)
        }

        OrderLog order = new OrderLog();
        order.setOrderId(UUID.randomUUID());
        order.setSignalId(signal.getSignalId());
        order.setStockCode(stockCode);
        order.setTradingMode(tradingMode);
        order.setOrderType(signal.getSignalType() == StrategySignal.SignalType.BUY
                ? OrderLog.OrderType.BUY : OrderLog.OrderType.SELL);
        order.setQuantity(quantity);
        order.setOrderPrice(price);

        // 4단계: 주문 단위 최종 검증 (종목당 포지션 한도, 최소 현금 비율) — BUG-002에서 구현된 게이트
        // Step 4: final order-level validation (per-stock position limit, min cash ratio) — the gate built for BUG-002
        RiskEngine.RiskCheckResult orderCheck = riskEngine.validateOrder(order, context);
        if (!orderCheck.approved()) {
            RiskEvent event = new RiskEvent(RiskEvent.EventType.POSITION_LIMIT, accountId, tradingMode,
                    "매수 주문 차단: " + orderCheck.rejectReason());
            riskEventMapper.insert(event); // useGeneratedKeys로 riskLogId가 event에 채워짐 (id is populated back via useGeneratedKeys)
            order.setBlockedByRiskId(event.getRiskLogId());
            order.setExecutionStatus(OrderLog.ExecutionStatus.REJECTED);
            orderLogMapper.insert(order); // 차단된 주문도 감사 추적을 위해 기록 (record blocked orders too, for audit trail)
            return Optional.of(order);
        }

        // 5단계: 실제 주문 실행 (모의/실전은 주입된 OrderExecutor 구현체가 결정)
        // Step 5: actual order execution (mock vs live is decided by the injected OrderExecutor bean)
        OrderLog executed = orderExecutor.execute(order);
        orderLogMapper.insert(executed);
        return Optional.of(executed);
    }

    private BigDecimal latestClosePrice(String stockCode) {
        List<PriceBar> bars = marketClient.getRecentPriceBars(stockCode, "DAILY", 1);
        return bars.isEmpty() ? null : bars.get(0).close();
    }

    private int calculateQuantity(BigDecimal totalAccountValue, BigDecimal price) {
        if (totalAccountValue == null || totalAccountValue.signum() <= 0 || price.signum() <= 0) {
            return 0;
        }
        BigDecimal targetOrderValue = totalAccountValue.multiply(targetPositionRatio);
        return targetOrderValue.divide(price, 0, RoundingMode.DOWN).intValue();
    }
}
