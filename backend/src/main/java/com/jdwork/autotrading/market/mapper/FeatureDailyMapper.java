package com.jdwork.autotrading.market.mapper;

import com.jdwork.autotrading.market.domain.FeatureDaily;
import org.apache.ibatis.annotations.Mapper;

/**
 * feature_daily 테이블 MyBatis 매퍼.
 * MyBatis mapper for the feature_daily table.
 */
@Mapper
public interface FeatureDailyMapper {

    /** 동일 종목·기준일·피처버전이면 갱신, 없으면 삽입. Upserts by (stock_code, base_date, feature_version). */
    void upsert(FeatureDaily feature);
}
