package com.jdwork.autotrading.backtest;

import java.math.BigDecimal;

/**
 * 백테스트 결과 지표 — 설계 §4.5 필수 지표(누적수익률, MDD, 승률, 샤프비율, 거래비용 반영)를 모두 포함.
 * Backtest result metrics — covers all required metrics from design doc §4.5
 * (cumulative return, MDD, win rate, Sharpe ratio, transaction cost impact).
 */
public record BacktestResult(
        int totalTrades,
        BigDecimal cumulativeReturnRate,
        BigDecimal maxDrawdownRate,
        BigDecimal winRate,
        BigDecimal sharpeRatio
) {
}
