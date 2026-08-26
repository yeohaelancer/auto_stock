package com.jdwork.autotrading.risk.mapper;

import com.jdwork.autotrading.risk.domain.RiskEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * risk_log 테이블 MyBatis 매퍼.
 * MyBatis mapper for the risk_log table.
 */
@Mapper
public interface RiskEventMapper {

    /** INSERT 후 생성된 risk_log_id를 event.riskLogId에 되돌려 채운다 (useGeneratedKeys). */
    /** After INSERT, the generated risk_log_id is written back into event.riskLogId (useGeneratedKeys). */
    void insert(RiskEvent event);

    /**
     * 최근 리스크 이벤트를 최신순으로 조회한다 — 모니터링 화면용.
     * Fetch recent risk events, newest first — for the monitoring dashboard.
     */
    List<RiskEvent> findRecentByMode(@Param("tradingMode") String tradingMode, @Param("limit") int limit);
}
