package com.jdwork.autotrading.order.mapper;

import com.jdwork.autotrading.order.domain.OrderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * order_log 테이블 MyBatis 매퍼. 모든 조회는 trading_mode 필터를 필수로 받는다
 * (모의/실전 데이터 혼동은 이 시스템에서 가장 위험한 버그 클래스 — Review Agent 지적사항).
 * MyBatis mapper for order_log. Every query requires an explicit trading_mode filter
 * (mock/live data mixups are the most dangerous bug class in this system — per Review Agent findings).
 */
@Mapper
public interface OrderLogMapper {

    void insert(OrderLog order);

    List<OrderLog> findRecentByMode(@Param("tradingMode") String tradingMode, @Param("limit") int limit);

    /**
     * 체결(FILLED) 주문의 누적 체결금액을 합산한다 — MOCK 모드 현금 잔고 산정에 사용 (BUG-006 수정).
     * Sums the executed value of FILLED orders — used to derive the MOCK-mode cash balance (BUG-006 fix).
     *
     * MOCK 모드에서는 시스템이 자금 흐름을 전부 통제하므로 "초기 시드머니 − 누적 매수 + 누적 매도"로
     * 현금 잔고를 정확히 계산할 수 있다. LIVE 모드에는 이 계산을 적용하지 않는다 (Review 필수 반영사항).
     * In MOCK mode the system controls the entire cash flow, so cash balance can be derived exactly as
     * "initial seed capital − cumulative buys + cumulative sells". This is never applied in LIVE mode
     * (per Review's must-fix finding).
     */
    BigDecimal sumFilledValue(@Param("tradingMode") String tradingMode, @Param("orderType") String orderType);

    /**
     * 체결(FILLED) 주문 전체를 종목·시각순으로 조회한다 — 백테스트의 FIFO 매매 쌍 구성에 사용 (BacktestService).
     * Fetch all FILLED orders ordered by stock/time — used to FIFO-match round-trip trades for backtesting.
     */
    List<OrderLog> findFilledByMode(@Param("tradingMode") String tradingMode);
}
