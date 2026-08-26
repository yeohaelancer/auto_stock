package com.jdwork.autotrading.market.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 시세/피처 수집 및 매매 유니버스 현황 요약 — 모니터링 화면용.
 * Summary of price/feature collection and the trading universe — for the monitoring dashboard.
 */
public class CollectionStatus {

    private long priceHistoryCount;
    private long priceHistoryStockCount;
    private OffsetDateTime priceHistoryLastCollectedAt;
    private long featureDailyCount;
    private long featureDailyStockCount;
    private LocalDate featureDailyLastBaseDate;
    private long universeTotalCount;
    private long universeActiveCount;

    public long getPriceHistoryCount() { return priceHistoryCount; }
    public void setPriceHistoryCount(long priceHistoryCount) { this.priceHistoryCount = priceHistoryCount; }
    public long getPriceHistoryStockCount() { return priceHistoryStockCount; }
    public void setPriceHistoryStockCount(long priceHistoryStockCount) { this.priceHistoryStockCount = priceHistoryStockCount; }
    public OffsetDateTime getPriceHistoryLastCollectedAt() { return priceHistoryLastCollectedAt; }
    public void setPriceHistoryLastCollectedAt(OffsetDateTime priceHistoryLastCollectedAt) { this.priceHistoryLastCollectedAt = priceHistoryLastCollectedAt; }
    public long getFeatureDailyCount() { return featureDailyCount; }
    public void setFeatureDailyCount(long featureDailyCount) { this.featureDailyCount = featureDailyCount; }
    public long getFeatureDailyStockCount() { return featureDailyStockCount; }
    public void setFeatureDailyStockCount(long featureDailyStockCount) { this.featureDailyStockCount = featureDailyStockCount; }
    public LocalDate getFeatureDailyLastBaseDate() { return featureDailyLastBaseDate; }
    public void setFeatureDailyLastBaseDate(LocalDate featureDailyLastBaseDate) { this.featureDailyLastBaseDate = featureDailyLastBaseDate; }
    public long getUniverseTotalCount() { return universeTotalCount; }
    public void setUniverseTotalCount(long universeTotalCount) { this.universeTotalCount = universeTotalCount; }
    public long getUniverseActiveCount() { return universeActiveCount; }
    public void setUniverseActiveCount(long universeActiveCount) { this.universeActiveCount = universeActiveCount; }
}
