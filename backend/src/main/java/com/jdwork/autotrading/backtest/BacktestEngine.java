package com.jdwork.autotrading.backtest;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 백테스트 성과 계산 엔진 (설계 §4.5).
 * Backtest performance calculation engine (design doc §4.5).
 *
 * 순수 계산 로직만 담당 — 어떤 거래 목록을 넣든(과거 시뮬레이션이든, 실제 체결된 order_log 이력이든)
 * 동일하게 동작한다. 데이터 조회/조립은 BacktestService가 담당한다.
 * Pure calculation only — works identically regardless of where the trade list came from
 * (simulated backtest or real order_log fills). Data lookup/assembly is BacktestService's job.
 *
 * ⚠️ 단순화 사항(운영 전 검토 필요) — Simplifications (review before production use):
 *   - 자본 곡선은 복리가 아닌 단리(초기자본 + 누적 순손익)로 근사한다.
 *     The equity curve approximates simple (additive) growth, not compounding.
 *   - 샤프비율은 무위험수익률 0, 연율화 없이 거래 단위 평균/표준편차만 사용한다.
 *     Sharpe ratio assumes a 0 risk-free rate and is not annualized — computed from raw per-trade returns.
 */
@Component
public class BacktestEngine {

    private static final int SCALE = 8;

    public BacktestResult run(List<BacktestTrade> trades, BigDecimal initialCapital,
                               BigDecimal commissionRate, BigDecimal slippageRate) {
        if (trades.isEmpty() || initialCapital == null || initialCapital.signum() <= 0) {
            return new BacktestResult(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<BacktestTrade> sorted = trades.stream()
                .sorted(Comparator.comparing(BacktestTrade::exitDate))
                .toList();

        BigDecimal capital = initialCapital;
        BigDecimal peak = initialCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        int wins = 0;
        List<BigDecimal> tradeReturns = new ArrayList<>(sorted.size());

        for (BacktestTrade trade : sorted) {
            BigDecimal quantity = BigDecimal.valueOf(trade.quantity());
            BigDecimal grossPnl = trade.exitPrice().subtract(trade.entryPrice()).multiply(quantity);

            // 매수+매도 양방향 거래대금 기준으로 수수료/슬리피지를 반영한다 (설계 §4.5 "거래비용 반영").
            // Transaction cost (commission + slippage) applied on the round-trip (buy+sell) notional
            // (design doc §4.5 "reflect transaction cost").
            BigDecimal roundTripNotional = trade.entryPrice().add(trade.exitPrice()).multiply(quantity);
            BigDecimal cost = roundTripNotional.multiply(commissionRate.add(slippageRate));
            BigDecimal netPnl = grossPnl.subtract(cost);

            BigDecimal entryNotional = trade.entryPrice().multiply(quantity);
            BigDecimal tradeReturn = entryNotional.signum() == 0
                    ? BigDecimal.ZERO
                    : netPnl.divide(entryNotional, SCALE, RoundingMode.HALF_UP);
            tradeReturns.add(tradeReturn);
            if (netPnl.signum() > 0) {
                wins++;
            }

            capital = capital.add(netPnl);
            if (capital.compareTo(peak) > 0) {
                peak = capital;
            } else if (peak.signum() > 0) {
                BigDecimal drawdown = peak.subtract(capital).divide(peak, SCALE, RoundingMode.HALF_UP);
                if (drawdown.compareTo(maxDrawdown) > 0) {
                    maxDrawdown = drawdown;
                }
            }
        }

        BigDecimal cumulativeReturnRate = capital.subtract(initialCapital)
                .divide(initialCapital, SCALE, RoundingMode.HALF_UP);
        BigDecimal winRate = BigDecimal.valueOf(wins)
                .divide(BigDecimal.valueOf(sorted.size()), SCALE, RoundingMode.HALF_UP);
        BigDecimal sharpeRatio = computeSharpeRatio(tradeReturns);

        return new BacktestResult(sorted.size(), cumulativeReturnRate, maxDrawdown, winRate, sharpeRatio);
    }

    private BigDecimal computeSharpeRatio(List<BigDecimal> returns) {
        if (returns.size() < 2) {
            return BigDecimal.ZERO; // 표준편차를 구할 수 없음 (not enough samples for a standard deviation)
        }
        BigDecimal mean = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), SCALE, RoundingMode.HALF_UP);
        BigDecimal sumSquaredDiff = returns.stream()
                .map(r -> r.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(returns.size() - 1), SCALE, RoundingMode.HALF_UP);
        double stdDev = Math.sqrt(variance.doubleValue());
        if (stdDev == 0) {
            return BigDecimal.ZERO;
        }
        return mean.divide(BigDecimal.valueOf(stdDev), SCALE, RoundingMode.HALF_UP);
    }
}
