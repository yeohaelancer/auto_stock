package com.jdwork.autotrading.account.domain;

import java.math.BigDecimal;

/**
 * 포지션 도메인 모델 (DB 테이블 position 매핑).
 * Position domain model (maps to position table).
 */
public class Position {

    private Long positionId;
    private String accountId;
    private String tradingMode; // MOCK / LIVE
    private String stockCode;
    private int quantity;
    private BigDecimal avgPrice;
    private BigDecimal stopLossPrice;
    private BigDecimal takeProfitPrice;

    public Long getPositionId() { return positionId; }
    public void setPositionId(Long positionId) { this.positionId = positionId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getTradingMode() { return tradingMode; }
    public void setTradingMode(String tradingMode) { this.tradingMode = tradingMode; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getAvgPrice() { return avgPrice; }
    public void setAvgPrice(BigDecimal avgPrice) { this.avgPrice = avgPrice; }
    public BigDecimal getStopLossPrice() { return stopLossPrice; }
    public void setStopLossPrice(BigDecimal stopLossPrice) { this.stopLossPrice = stopLossPrice; }
    public BigDecimal getTakeProfitPrice() { return takeProfitPrice; }
    public void setTakeProfitPrice(BigDecimal takeProfitPrice) { this.takeProfitPrice = takeProfitPrice; }
}
