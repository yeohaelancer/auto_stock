package com.jdwork.autotrading.market.mapper;

import com.jdwork.autotrading.market.dto.CollectionStatus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 시세/피처 수집 현황 조회 전용 매퍼 — 모니터링 화면용 (특정 테이블 하나가 아니라 집계 요약을 반환).
 * Mapper dedicated to collection-status lookups — for the monitoring dashboard (returns an aggregate
 * summary rather than a single table's rows).
 */
@Mapper
public interface MarketStatusMapper {

    /** 시세/피처/유니버스 수집 현황을 한 번의 쿼리로 집계해 반환한다. Aggregates price/feature/universe status in a single query. */
    CollectionStatus findCollectionStatus();
}
