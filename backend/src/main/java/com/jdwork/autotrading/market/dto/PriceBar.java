package com.jdwork.autotrading.market.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 시세 봉 데이터 (일봉/분봉 공용).
 * A single OHLCV price bar (shared for daily/minute intervals).
 */
public record PriceBar(
        String stockCode,
        String intervalType, // DAILY / MINUTE
        OffsetDateTime tradeDateTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
