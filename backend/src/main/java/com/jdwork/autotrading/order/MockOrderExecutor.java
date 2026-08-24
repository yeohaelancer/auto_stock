package com.jdwork.autotrading.order;

import com.jdwork.autotrading.order.domain.OrderLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 모의투자 주문 실행기 — 키움 모의투자 API를 호출하거나(연동 전에는) 즉시 체결로 시뮬레이션한다.
 * Mock/paper trading order executor — calls the Kiwoom paper trading API, or (before that wiring)
 * simulates an immediate fill.
 */
@Component
@ConditionalOnProperty(name = "trading.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockOrderExecutor implements OrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(MockOrderExecutor.class);

    @Override
    public OrderLog execute(OrderLog order) {
        // TODO: 키움 모의투자 REST API 주문 엔드포인트 연동 (integrate Kiwoom paper trading order endpoint)
        order.setExecutedPrice(order.getOrderPrice());
        order.setExecutionStatus(OrderLog.ExecutionStatus.FILLED);
        log.info("[MOCK] 주문 체결 시뮬레이션: {} {} {}주", order.getStockCode(), order.getOrderType(), order.getQuantity());
        return order;
    }
}
