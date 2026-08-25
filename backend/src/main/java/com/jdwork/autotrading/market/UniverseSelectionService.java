package com.jdwork.autotrading.market;

import com.jdwork.autotrading.stock.mapper.StockMasterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 거래대금 상위 종목을 자동으로 조회해 매매 유니버스(stock_master)를 채우는 서비스.
 * Automatically fetches top-trading-value stocks to populate the trading universe (stock_master).
 *
 * 사용자 요청 반영: "매매 종목을 사람이 직접 지정하지 않고 시스템이 스스로 선정" — 코스피/코스닥
 * 각각 거래대금 상위를 조회해 합친 뒤, 전체 기준 거래대금 상위 N개를 최종 유니버스로 확정한다
 * (설계 §5.3 "거래대금/시가총액 하한선 — 유동성 낮은 종목 제외" 원칙과 일치).
 * Per user request: "the system should pick trading targets itself, not a human" — fetches top
 * trading-value stocks from KOSPI and KOSDAQ separately, merges them, and keeps the overall top N
 * by trading value as the final universe (matches design doc §5.3's liquidity-floor principle).
 */
@Service
public class UniverseSelectionService {

    private static final Logger log = LoggerFactory.getLogger(UniverseSelectionService.class);

    private final KiwoomRankingClient rankingClient;
    private final StockMasterMapper stockMasterMapper;
    private final int universeSize;

    public UniverseSelectionService(KiwoomRankingClient rankingClient,
                                     StockMasterMapper stockMasterMapper,
                                     @Value("${trading.universe.auto-select-size}") int universeSize) {
        this.rankingClient = rankingClient;
        this.stockMasterMapper = stockMasterMapper;
        this.universeSize = universeSize;
    }

    /**
     * 코스피+코스닥 거래대금 상위를 합쳐 상위 {@code universeSize}개를 stock_master에 반영한다.
     * 조회 결과가 비어있으면(API 미연동/실패) 기존 유니버스를 그대로 두고 아무것도 하지 않는다 —
     * 일시적 조회 실패로 유니버스 전체가 비워지는 사고를 방지한다 (설계 §10 원칙).
     *
     * Merges KOSPI+KOSDAQ top trading-value stocks and applies the overall top {@code universeSize} to
     * stock_master. If the lookup comes back empty (API not wired/failed), leaves the existing universe
     * untouched — prevents a transient lookup failure from wiping out the whole universe (design doc §10).
     */
    public void refreshAutoUniverse() {
        List<Candidate> combined = new ArrayList<>();
        combined.addAll(fetchMarket(KiwoomRankingClient.MARKET_KOSPI_CODE, "KOSPI"));
        combined.addAll(fetchMarket(KiwoomRankingClient.MARKET_KOSDAQ_CODE, "KOSDAQ"));

        if (combined.isEmpty()) {
            log.warn("거래대금상위 조회 결과가 비어있어 유니버스 갱신을 스킵합니다 "
                    + "(top-trading-value lookup returned nothing — skipping universe refresh)");
            return;
        }

        List<Candidate> topN = combined.stream()
                .sorted(Comparator.comparing((Candidate c) -> c.stock().tradingValueMillionWon()).reversed())
                .limit(universeSize)
                .toList();

        List<String> keptCodes = new ArrayList<>();
        for (Candidate candidate : topN) {
            try {
                stockMasterMapper.upsertAutoSelected(candidate.stock().stockCode(), candidate.stock().stockName(), candidate.marketType());
                keptCodes.add(candidate.stock().stockCode());
            } catch (Exception e) {
                log.error("{} 종목 자동선정 반영 실패 — 다음 종목으로 계속 진행 "
                                + "({} auto-selection upsert failed, continuing with the next stock)",
                        candidate.stock().stockCode(), candidate.stock().stockCode(), e);
            }
        }

        int deactivated = stockMasterMapper.deactivateAutoSelectedNotIn(keptCodes);
        log.info("유니버스 자동선정 완료: {}종목 반영, {}종목 제외 "
                        + "(auto universe selection complete: {} stocks kept, {} stocks removed)",
                keptCodes.size(), deactivated, keptCodes.size(), deactivated);
    }

    private List<Candidate> fetchMarket(String marketCode, String marketLabel) {
        try {
            return rankingClient.fetchTopByTradingValue(marketCode).stream()
                    .map(stock -> new Candidate(stock, marketLabel))
                    .toList();
        } catch (Exception e) {
            log.error("{} 시장 거래대금상위 조회 중 오류 — 다음 시장으로 계속 진행 "
                    + "({} market ranking lookup failed, continuing with the next market)", marketLabel, marketLabel, e);
            return List.of();
        }
    }

    private record Candidate(KiwoomRankingClient.RankedStock stock, String marketType) {
    }
}
