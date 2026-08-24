package com.jdwork.autotrading.order.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 주문 이력 도메인 모델 (DB 테이블 order_log 매핑).
 * Order history domain model (maps to order_log table).
 */
public class OrderLog {

    public enum OrderType { BUY, SELL }
    public enum ExecutionStatus { PENDING, FILLED, PARTIAL, CANCELLED, REJECTED }

    private UUID orderId;
    private UUID signalId;
    private String stockCode;
    private String tradingMode; // MOCK / LIVE — 모의/실전 데이터 혼동 방지 핵심 컬럼 (critical for preventing mock/live data mixups)
    private OrderType orderType;
    private int quantity;
    private BigDecimal orderPrice;
    private BigDecimal executedPrice;
    private ExecutionStatus executionStatus = ExecutionStatus.PENDING;
    private Long blockedByRiskId;
    private String kiwoomOrderNo;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getSignalId() { return signalId; }
    public void setSignalId(UUID signalId) { this.signalId = signalId; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public String getTradingMode() { return tradingMode; }
    public void setTradingMode(String tradingMode) { this.tradingMode = tradingMode; }
    public OrderType getOrderType() { return orderType; }
    public void setOrderType(OrderType orderType) { this.orderType = orderType; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getOrderPrice() { return orderPrice; }
    public void setOrderPrice(BigDecimal orderPrice) { this.orderPrice = orderPrice; }
    public BigDecimal getExecutedPrice() { return executedPrice; }
    public void setExecutedPrice(BigDecimal executedPrice) { this.executedPrice = executedPrice; }
    public ExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(ExecutionStatus executionStatus) { this.executionStatus = executionStatus; }
    public Long getBlockedByRiskId() { return blockedByRiskId; }
    public void setBlockedByRiskId(Long blockedByRiskId) { this.blockedByRiskId = blockedByRiskId; }
    public String getKiwoomOrderNo() { return kiwoomOrderNo; }
    public void setKiwoomOrderNo(String kiwoomOrderNo) { this.kiwoomOrderNo = kiwoomOrderNo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
