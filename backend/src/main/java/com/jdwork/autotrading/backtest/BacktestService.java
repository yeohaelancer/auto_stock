package com.jdwork.autotrading.backtest;

import com.jdwork.autotrading.order.domain.OrderLog;
import com.jdwork.autotrading.order.mapper.OrderLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * order_log에 쌓인 체결 이력을 FIFO로 매수-매도 쌍(BacktestTrade)으로 묶어 BacktestEngine에 넘긴다.
 * FIFO-matches order_log fills into round-trip trades (BacktestTrade) and feeds them to BacktestEngine.
 *
 * 실제 시뮬레이션(과거 시세 재생) 백테스트가 아니라, 이미 쌓인 MOCK 체결 기록으로 성과를 사후 분석하는
 * 용도다 — Phase 1 시점에는 체결 이력이 없을 수 있으므로 결과가 비어 있을 수 있다(정상).
 * This is not a historical-replay simulation — it retrospectively analyzes fills already recorded
 * (e.g. from MOCK trading). At the current Phase-1 stage there may be no fills yet, so an empty
 * result is expected, not a bug.
 */
@Service
public class BacktestService {

    private final OrderLogMapper orderLogMapper;
    private final BacktestEngine backtestEngine;
    private final BigDecimal commissionRate;
    private final BigDecimal slippageRate;
    private final BigDecimal defaultInitialCapital;

    public BacktestService(OrderLogMapper orderLogMapper,
                            BacktestEngine backtestEngine,
                            @Value("${trading.backtest.commission-rate}") BigDecimal commissionRate,
                            @Value("${trading.backtest.slippage-rate}") BigDecimal slippageRate,
                            @Value("${trading.mock.initial-capital}") BigDecimal defaultInitialCapital) {
        this.orderLogMapper = orderLogMapper;
        this.backtestEngine = backtestEngine;
        this.commissionRate = commissionRate;
        this.slippageRate = slippageRate;
        this.defaultInitialCapital = defaultInitialCapital;
    }

    public BacktestResult runForMode(String tradingMode, BigDecimal initialCapital) {
        List<OrderLog> filled = orderLogMapper.findFilledByMode(tradingMode);
        List<BacktestTrade> trades = matchRoundTrips(filled);
        BigDecimal capital = initialCapital != null ? initialCapital : defaultInitialCapital;
        return backtestEngine.run(trades, capital, commissionRate, slippageRate);
    }

    /**
     * 종목별로 매수 체결을 큐에 쌓고, 매도 체결이 들어올 때마다 FIFO로 소진하며 거래 쌍을 만든다.
     * Queues BUY fills per stock; each SELL fill consumes them FIFO to form round-trip trades.
     *
     * 매도 수량이 보유 매수 수량을 초과하는 경우(데이터 정합성 문제 또는 공매도)는 이 시스템의
     * 범위(현물 매수 전제) 밖이므로 매칭되지 않는 잔여 수량은 무시한다.
     * If a SELL's quantity exceeds queued BUY quantity (data inconsistency, or a short sale) — out of
     * scope for this long-only system — the unmatched remainder is simply dropped.
     */
    private List<BacktestTrade> matchRoundTrips(List<OrderLog> filledOrders) {
        Map<String, Deque<Lot>> openLotsByStock = new HashMap<>();
        List<BacktestTrade> trades = new ArrayList<>();

        for (OrderLog order : filledOrders) {
            Deque<Lot> lots = openLotsByStock.computeIfAbsent(order.getStockCode(), k -> new ArrayDeque<>());

            if (order.getOrderType() == OrderLog.OrderType.BUY) {
                lots.addLast(new Lot(order.getExecutedPrice(), order.getQuantity(), order.getCreatedAt().toLocalDate()));
                continue;
            }

            // SELL: FIFO로 매수 랏을 소진 (consume BUY lots FIFO)
            int remaining = order.getQuantity();
            LocalDate exitDate = order.getCreatedAt().toLocalDate();
            BigDecimal exitPrice = order.getExecutedPrice();

            while (remaining > 0 && !lots.isEmpty()) {
                Lot lot = lots.peekFirst();
                int matchedQty = Math.min(remaining, lot.quantity());
                trades.add(new BacktestTrade(order.getStockCode(), lot.entryDate(), lot.price(), exitDate, exitPrice, matchedQty));
                remaining -= matchedQty;

                lots.pollFirst();
                if (matchedQty < lot.quantity()) {
                    lots.addFirst(new Lot(lot.price(), lot.quantity() - matchedQty, lot.entryDate()));
                }
            }
        }

        return trades;
    }

    private record Lot(BigDecimal price, int quantity, LocalDate entryDate) {
    }
}
