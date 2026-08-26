package com.jdwork.autotrading.strategy.mapper;

import com.jdwork.autotrading.strategy.domain.StrategySignal;
import org.apache.ibatis.annotations.Mapper;

/**
 * trading_strategy_signal 테이블 MyBatis 매퍼.
 * MyBatis mapper for the trading_strategy_signal table.
 *
 * ⚠️ 실운영 중 발견: OrderService가 이 매퍼 없이 StrategySignal을 메모리상에서만 만들고
 * trading_order_log에만 곧장 insert하고 있었다 — trading_order_log.signal_id가 trading_strategy_signal을
 * 참조하는 FK라서, 실제로 신호가 임계값(0.6)을 넘어 주문까지 이어지는 순간 매번
 * "a foreign key constraint fails" 오류로 실패하고 있었다. 이 매퍼가 그 빠진 고리다.
 * ⚠️ Found in real operation: OrderService only ever built a StrategySignal in memory and inserted
 * straight into trading_order_log — but trading_order_log.signal_id is an FK into
 * trading_strategy_signal, so the moment a signal actually cleared the 0.6 confidence threshold and
 * reached order creation, it failed every time with "a foreign key constraint fails". This mapper is
 * the missing link.
 */
@Mapper
public interface StrategySignalMapper {

    void insert(StrategySignal signal);
}
