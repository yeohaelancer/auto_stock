package com.jdwork.autotrading.stock.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * stock_master 테이블 MyBatis 매퍼.
 * MyBatis mapper for the stock_master table.
 */
@Mapper
public interface StockMasterMapper {

    /**
     * 관리종목/거래정지 종목을 제외한 매매 대상 종목코드 목록을 조회한다 (설계 §5.3 종목 유니버스 필터링).
     * Fetch tradable stock codes, excluding managed/halted stocks (design doc §5.3 universe filtering).
     */
    List<String> findActiveUniverse();

    /**
     * cutoff 이후 price_history에 시세가 한 건도 없는 종목을 거래정지 후보로 표시한다 (preMarketJob, 휴리스틱).
     * Flags stocks with no price_history rows since cutoff as suspected halted (preMarketJob, heuristic).
     *
     * 키움 API의 실제 거래정지 통지가 아니라 "최근 시세 부재"라는 대리 신호를 쓰는 임시 방편이다 —
     * 정식 거래정지/관리종목 지정 피드 연동 전까지의 안전판(fail-safe)일 뿐, 완전한 대체재는 아니다.
     * This is a proxy signal ("no recent price"), not Kiwoom's actual halt notice — a fail-safe until a
     * real halt/managed-stock designation feed is wired in, not a full replacement for one.
     */
    int markStaleAsHalted(@Param("cutoff") OffsetDateTime cutoff);

    /** 위 마킹 이후 다시 시세가 관측된 종목의 거래정지 표시를 해제한다 (자동 복구). Un-flags stocks once fresh price data reappears (auto-recovery). */
    int markActiveAsResumed(@Param("cutoff") OffsetDateTime cutoff);
}
