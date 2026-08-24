package com.jdwork.autotrading.risk.domain;

import java.time.OffsetDateTime;

/**
 * 리스크 이벤트 도메인 모델 (DB 테이블 risk_log 매핑).
 * Risk event domain model (maps to risk_log table).
 */
public class RiskEvent {

    public enum EventType { DAILY_LOSS_LIMIT, POSITION_LIMIT, ANOMALY_DETECTED, MANUAL_KILL_SWITCH }

    private Long riskLogId;
    private EventType eventType;
    private OffsetDateTime occurredAt;
    private String accountId;
    private String tradingMode; // MOCK / LIVE
    private String actionTaken;

    public RiskEvent(EventType eventType, String accountId, String tradingMode, String actionTaken) {
        this.eventType = eventType;
        this.accountId = accountId;
        this.tradingMode = tradingMode;
        this.actionTaken = actionTaken;
        this.occurredAt = OffsetDateTime.now();
    }

    public Long getRiskLogId() { return riskLogId; }
    public void setRiskLogId(Long riskLogId) { this.riskLogId = riskLogId; }
    public EventType getEventType() { return eventType; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getAccountId() { return accountId; }
    public String getTradingMode() { return tradingMode; }
    public String getActionTaken() { return actionTaken; }
}
