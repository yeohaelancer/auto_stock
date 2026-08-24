package com.jdwork.autotrading.strategy.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 매매 신호 도메인 모델 (DB 테이블 strategy_signal 매핑).
 * Trading signal domain model (maps to strategy_signal table).
 */
public class StrategySignal {

    private UUID signalId;
    private String stockCode;
    private Long predictionId;
    private SignalType signalType;
    private OffsetDateTime generatedAt;
    private SignalStatus status;
    private String rejectReason;

    public enum SignalType { BUY, SELL, HOLD }

    public enum SignalStatus { PENDING, APPROVED, REJECTED, EXPIRED }

    // getters/setters 생략 표기 — Lombok @Data 적용 예정 (getters/setters omitted here — Lombok @Data to be applied)
    public UUID getSignalId() { return signalId; }
    public void setSignalId(UUID signalId) { this.signalId = signalId; }
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public Long getPredictionId() { return predictionId; }
    public void setPredictionId(Long predictionId) { this.predictionId = predictionId; }
    public SignalType getSignalType() { return signalType; }
    public void setSignalType(SignalType signalType) { this.signalType = signalType; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
    public SignalStatus getStatus() { return status; }
    public void setStatus(SignalStatus status) { this.status = status; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
