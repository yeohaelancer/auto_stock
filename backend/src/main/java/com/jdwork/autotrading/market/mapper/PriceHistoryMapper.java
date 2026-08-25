package com.jdwork.autotrading.market.mapper;

import com.jdwork.autotrading.market.dto.PriceBar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * price_history 테이블 MyBatis 매퍼 — 시세 수집 배치(TradingScheduler)와 AI 피처 계산(FeatureEngineeringService)에서 사용.
 * MyBatis mapper for price_history — used by the price-collection batch (TradingScheduler) and
 * AI feature computation (FeatureEngineeringService).
 */
@Mapper
public interface PriceHistoryMapper {

    /** 동일 종목·봉종류·시각이면 갱신, 없으면 삽입 (중복 수집 방지). Upserts by (stock_code, interval_type, trade_datetime). */
    void upsert(PriceBar bar);

    /**
     * 최신순으로 최근 N개 봉을 조회한다 (지표 계산용). 호출부가 시간순 정렬이 필요하면 직접 뒤집어야 한다.
     * Fetch the most recent N bars in descending order (for indicator computation). Callers needing
     * chronological order must reverse the list themselves.
     */
    List<PriceBar> findRecent(@Param("stockCode") String stockCode,
                               @Param("intervalType") String intervalType,
                               @Param("limit") int limit);
}
