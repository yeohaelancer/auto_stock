package com.jdwork.autotrading.account.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 계좌 스냅샷 도메인 모델 (DB 테이블 account_snapshot 매핑).
 * Account snapshot domain model (maps to account_snapshot table).
 */
public class AccountSnapshot {

    private String accountId;
    private String tradingMode; // MOCK / LIVE
    private LocalDate snapshotDate;
    private BigDecimal totalValue;
    private BigDecimal cashBalance;
    private BigDecimal dailyPnl;
    private BigDecimal dailyPnlRate;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getTradingMode() { return tradingMode; }
    public void setTradingMode(String tradingMode) { this.tradingMode = tradingMode; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }
    public BigDecimal getCashBalance() { return cashBalance; }
    public void setCashBalance(BigDecimal cashBalance) { this.cashBalance = cashBalance; }
    public BigDecimal getDailyPnl() { return dailyPnl; }
    public void setDailyPnl(BigDecimal dailyPnl) { this.dailyPnl = dailyPnl; }
    public BigDecimal getDailyPnlRate() { return dailyPnlRate; }
    public void setDailyPnlRate(BigDecimal dailyPnlRate) { this.dailyPnlRate = dailyPnlRate; }
}
