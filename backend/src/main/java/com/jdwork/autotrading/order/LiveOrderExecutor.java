package com.jdwork.autotrading.order;

import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.order.domain.OrderLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실거래 주문 실행기 — 키움증권 REST API로 실제 주문을 전송한다.
 * Live order executor — sends real orders via the Kiwoom REST API.
 *
 * trading.mode=LIVE일 때만 빈으로 등록되며, 이 클래스가 호출되는 것 자체가 실제 자금이 움직인다는 뜻이다.
 * Only registered when trading.mode=LIVE. Invoking this class means real money moves.
 */
@Component
@ConditionalOnProperty(name = "trading.mode", havingValue = "LIVE")
public class LiveOrderExecutor implements OrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderExecutor.class);

    private final KiwoomApiProperties kiwoomApiProperties;

    public LiveOrderExecutor(KiwoomApiProperties kiwoomApiProperties) {
        this.kiwoomApiProperties = kiwoomApiProperties;
    }

    @Override
    public OrderLog execute(OrderLog order) {
        // TODO: 키움 실전 REST API 주문 엔드포인트 연동. 반드시 별도 수동 승인 절차를 거친 후에만 호출될 것 (설계 §3.3).
        // TODO: Integrate the Kiwoom live REST API order endpoint. Must only be invoked after a separate manual approval step (design doc §3.3).
        log.warn("[LIVE] 실거래 주문 실행: {} {} {}주 (account={})",
                order.getStockCode(), order.getOrderType(), order.getQuantity(), kiwoomApiProperties.getAccountNo());
        order.setExecutionStatus(OrderLog.ExecutionStatus.PENDING);
        return order;
    }
}
