package com.jdwork.autotrading.account.mapper;

import com.jdwork.autotrading.account.domain.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * position 테이블 MyBatis 매퍼. order_log와 마찬가지로 trading_mode 필터를 필수로 받아
 * 모의/실전 포지션 혼동을 원천 차단한다 (Review Agent 지적사항).
 * MyBatis mapper for the position table. Like order_log, always requires a trading_mode filter
 * to prevent mock/live position mixups (per Review Agent findings).
 */
@Mapper
public interface PositionMapper {

    List<Position> findByAccountAndMode(@Param("accountId") String accountId, @Param("tradingMode") String tradingMode);

    /**
     * 키움 실계좌 잔고 조회 결과로 포지션을 갱신한다(upsert) — LIVE 모드 postMarketJob에서 사용.
     * quantity=0인 종목도 그대로 저장되며, findByAccountAndMode의 "quantity > 0" 필터가 자연스럽게
     * 청산된 포지션을 화면에서 숨겨준다 (별도 삭제 로직 불필요).
     * Upserts a position from a real Kiwoom account balance lookup — used by the LIVE-mode
     * postMarketJob. Entries with quantity=0 are still stored; findByAccountAndMode's
     * "quantity > 0" filter naturally hides closed-out positions (no separate delete needed).
     */
    void upsertFromBalance(Position position);
}
