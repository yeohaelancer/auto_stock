package com.jdwork.autotrading.risk.mapper;

import com.jdwork.autotrading.risk.domain.RiskEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * risk_log 테이블 MyBatis 매퍼.
 * MyBatis mapper for the risk_log table.
 */
@Mapper
public interface RiskEventMapper {

    /** INSERT 후 생성된 risk_log_id를 event.riskLogId에 되돌려 채운다 (useGeneratedKeys). */
    /** After INSERT, the generated risk_log_id is written back into event.riskLogId (useGeneratedKeys). */
    void insert(RiskEvent event);
}
