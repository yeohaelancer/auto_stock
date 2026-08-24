package com.jdwork.autotrading.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 백테스트 대상 개별 거래(매수-매도 1쌍, 현물 매수 전제 — 공매도 미지원).
 * A single round-trip trade for backtesting (long-only — short selling not supported).
 */
public record BacktestTrade(
        String stockCode,
        LocalDate entryDate,
        BigDecimal entryPrice,
        LocalDate exitDate,
        BigDecimal exitPrice,
        int quantity
) {
}
