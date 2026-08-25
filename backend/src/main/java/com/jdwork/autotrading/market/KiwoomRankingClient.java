package com.jdwork.autotrading.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.market.auth.KiwoomTokenClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 키움증권 거래대금상위요청 TR(ka10032) 클라이언트 — 매매 유니버스 자동선정에 사용.
 * Kiwoom top-trading-value TR (ka10032) client — used for automatic trading universe selection.
 *
 * ✅ 키움 공식 개발자 포털 문서로 확인 후 구현했다 (2026-08-24, 사용자가 포털에서 직접 확인한
 *    요청/응답 스펙 기준). POST /api/dostk/rkinfo.
 * ✅ Implemented per the Kiwoom official developer portal spec (verified 2026-08-24 from
 *    screenshots the user pulled from the portal). POST /api/dostk/rkinfo.
 *
 * 관리종목은 요청 단계에서 제외(mang_stk_incls=0)한다 — 설계 §5.3 "관리종목 자동 제외" 원칙.
 * Managed stocks are excluded at the request level (mang_stk_incls=0) — design doc §5.3 principle.
 */
@Component
public class KiwoomRankingClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomRankingClient.class);

    private static final String TOP_TRADING_VALUE_TR = "ka10032";
    private static final String RANKING_PATH = "/api/dostk/rkinfo";
    private static final String EXCLUDE_MANAGED = "0"; // mang_stk_incls: 0=관리종목 미포함
    private static final String EXCHANGE_KRX = "1";     // stex_tp: 1=KRX (다른 클라이언트들과 KRX 일관성 유지)

    public static final String MARKET_KOSPI_CODE = "001";
    public static final String MARKET_KOSDAQ_CODE = "101";

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;

    public KiwoomRankingClient(WebClient.Builder builder, KiwoomApiProperties properties, KiwoomTokenClient tokenClient) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenClient = tokenClient;
    }

    /** 지정 시장(001=코스피, 101=코스닥)의 거래대금 상위 종목 목록을 조회한다. 실패 시 빈 목록. */
    public List<RankedStock> fetchTopByTradingValue(String marketTypeCode) {
        try {
            RankingResponse response = webClient.post()
                    .uri(RANKING_PATH)
                    .header("authorization", "Bearer " + tokenClient.getAccessToken())
                    .header("api-id", TOP_TRADING_VALUE_TR)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                    .bodyValue(new RankingRequest(marketTypeCode, EXCLUDE_MANAGED, EXCHANGE_KRX))
                    .retrieve()
                    .bodyToMono(RankingResponse.class)
                    .block();

            if (response == null || response.entries() == null) {
                return List.of();
            }

            return response.entries().stream()
                    .map(this::toRankedStock)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("키움 거래대금상위 조회 실패 — market={} (Kiwoom top-trading-value lookup failed for market={})",
                    marketTypeCode, marketTypeCode, e);
            return List.of(); // 실패 시 절대 임의 목록으로 대체하지 않음 (never fabricate a list on failure)
        }
    }

    private RankedStock toRankedStock(RankingEntry entry) {
        try {
            BigDecimal tradingValue = parseAmount(entry.tradingValue());
            return new RankedStock(entry.stockCode(), entry.stockName(), tradingValue);
        } catch (Exception e) {
            log.warn("거래대금상위 응답 항목 파싱 실패 — 해당 항목 스킵 (failed to parse a ranking entry, skipping it)", e);
            return null;
        }
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = value.startsWith("+") ? value.substring(1) : value;
        return new BigDecimal(cleaned.trim());
    }

    /** 거래대금 상위 종목 1건. tradingValueMillionWon 단위는 백만원(TR 응답 그대로). */
    public record RankedStock(String stockCode, String stockName, BigDecimal tradingValueMillionWon) {
    }

    private record RankingRequest(
            @JsonProperty("mrkt_tp") String marketType,
            @JsonProperty("mang_stk_incls") String excludeManaged,
            @JsonProperty("stex_tp") String exchangeType
    ) {
    }

    private record RankingResponse(
            @JsonProperty("trde_prica_upper") List<RankingEntry> entries
    ) {
    }

    private record RankingEntry(
            @JsonProperty("stk_cd") String stockCode,
            @JsonProperty("stk_nm") String stockName,
            @JsonProperty("trde_prica") String tradingValue
    ) {
    }
}
