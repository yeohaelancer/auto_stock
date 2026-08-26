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

    /**
     * DB 조회 결과를 그대로 복원하기 위한 생성자 — MyBatis resultMap의 &lt;constructor&gt;가 사용한다
     * (occurredAt/riskLogId를 now()/null로 덮어쓰지 않고 저장된 값 그대로 채워야 하므로 위 생성자와 분리).
     * Reconstruction constructor used by MyBatis resultMap's &lt;constructor&gt; (kept separate from the
     * constructor above so occurredAt/riskLogId are restored from the DB, not overwritten with now()/null).
     */
    public RiskEvent(Long riskLogId, EventType eventType, OffsetDateTime occurredAt,
                      String accountId, String tradingMode, String actionTaken) {
        this.riskLogId = riskLogId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.accountId = accountId;
        this.tradingMode = tradingMode;
        this.actionTaken = actionTaken;
    }

    public Long getRiskLogId() { return riskLogId; }
    public void setRiskLogId(Long riskLogId) { this.riskLogId = riskLogId; }
    public EventType getEventType() { return eventType; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getAccountId() { return accountId; }
    public String getTradingMode() { return tradingMode; }
    public String getActionTaken() { return actionTaken; }
}
