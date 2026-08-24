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
}
