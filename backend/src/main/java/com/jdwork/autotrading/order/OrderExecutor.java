package com.jdwork.autotrading.order;

import com.jdwork.autotrading.order.domain.OrderLog;

/**
 * 주문 실행 인터페이스 — 모의/실전 전환을 Strategy 패턴으로 캡슐화한다 (설계 §3.3).
 * Order execution interface — encapsulates mock/live switching via the Strategy pattern (design doc §3.3).
 *
 * 실거래(LiveOrderExecutor)와 모의투자(MockOrderExecutor)는 동일 인터페이스를 구현하며,
 * 어떤 구현체가 주입되는지는 trading.mode 설정값에 의해서만 결정된다.
 * Live and mock implementations share this interface; which one is wired in is decided
 * solely by the trading.mode config value — never by a code change.
 */
public interface OrderExecutor {

    /** 주문을 실행하고 결과가 반영된 주문 이력을 반환한다. Execute the order and return the resulting order log. */
    OrderLog execute(OrderLog order);
}
