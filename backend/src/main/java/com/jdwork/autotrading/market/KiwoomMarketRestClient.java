package com.jdwork.autotrading.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.market.auth.KiwoomTokenClient;
import com.jdwork.autotrading.market.dto.PriceBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 키움증권 REST API 시세 클라이언트.
 * Kiwoom REST API market data client.
 *
 * ✅ 인증(OAuth 접근토큰)은 KiwoomTokenClient로 실연동 완료.
 * ✅ Authentication (OAuth access token) is genuinely wired via KiwoomTokenClient.
 *
 * ✅ 일봉 조회는 "일별주가요청(TR: ka10086)" — 키움 공식 개발자 포털 문서로 확인 후 구현했다
 *    (2026-08-24, 사용자가 포털에서 직접 확인한 요청/응답 스펙 기준).
 * ✅ Daily-bar lookup uses "daily price request (TR: ka10086)" — implemented per the Kiwoom official
 *    developer portal spec (verified 2026-08-24 from screenshots the user pulled from the portal).
 *
 * ⚠️ TODO: 분봉(MINUTE) 조회 TR은 아직 미확인 — 요청 시 빈 목록 반환.
 * ⚠️ TODO: minute-bar TR not yet verified — returns an empty list if requested.
 */
@Component
public class KiwoomMarketRestClient implements KiwoomMarketClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomMarketRestClient.class);

    private static final String DAILY_PRICE_TR = "ka10086";
    private static final String DAILY_PRICE_PATH = "/api/dostk/mrkcond";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;

    public KiwoomMarketRestClient(WebClient.Builder builder, KiwoomApiProperties properties, KiwoomTokenClient tokenClient) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenClient = tokenClient;
    }

    @Override
    public List<PriceBar> getRecentPriceBars(String stockCode, String intervalType, int count) {
        if (!"DAILY".equals(intervalType)) {
            log.warn("분봉(MINUTE) TR 미검증 — {} 종목 조회 스킵 (minute-bar TR not verified yet, skipping {})",
                    stockCode, stockCode);
            return List.of();
        }

        try {
            String queryDate = LocalDate.now(KST).format(YYYYMMDD);
            DailyPriceResponse response = webClient.post()
                    .uri(DAILY_PRICE_PATH)
                    .header("authorization", "Bearer " + tokenClient.getAccessToken())
                    .header("api-id", DAILY_PRICE_TR)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                    .bodyValue(new DailyPriceRequest(stockCode, queryDate, "0"))
                    .retrieve()
                    .bodyToMono(DailyPriceResponse.class)
                    .block();

            if (response == null || response.dalyStkpc() == null) {
                return List.of();
            }

            return response.dalyStkpc().stream()
                    .map(entry -> toPriceBar(stockCode, entry))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(PriceBar::tradeDateTime).reversed()) // 응답 정렬 순서를 가정하지 않고 직접 최신순 정렬
                    .limit(count)
                    .toList();
        } catch (Exception e) {
            // 조회 실패 시 절대 임의 값으로 대체하지 않고 빈 목록 반환 (설계 §10)
            // Never fabricate a value on failure — return an empty list instead (design doc §10)
            log.error("키움 일별주가 조회 실패 — {} 종목 (Kiwoom daily price lookup failed for {})", stockCode, stockCode, e);
            return List.of();
        }
    }

    private PriceBar toPriceBar(String stockCode, DailyPriceEntry entry) {
        try {
            LocalDate date = LocalDate.parse(entry.date(), YYYYMMDD);
            return new PriceBar(
                    stockCode,
                    "DAILY",
                    date.atStartOfDay(KST).toOffsetDateTime(),
                    new BigDecimal(entry.openPric()),
                    new BigDecimal(entry.highPric()),
                    new BigDecimal(entry.lowPric()),
                    new BigDecimal(entry.closePric()),
                    parseVolume(entry.tradeQty())
            );
        } catch (Exception e) {
            log.warn("일별주가 응답 항목 파싱 실패 — 해당 항목 스킵 (failed to parse a daily-price entry, skipping it)", e);
            return null;
        }
    }

    /** trde_qty(거래량)는 "단위: 1주"로 부호 표기가 없는 필드이나, 방어적으로 선행 '+'만 제거하고 파싱한다. */
    /** trde_qty (volume) is documented without a sign prefix, but strip a leading '+' defensively before parsing. */
    private long parseVolume(String tradeQty) {
        if (tradeQty == null || tradeQty.isBlank()) {
            return 0L;
        }
        String cleaned = tradeQty.startsWith("+") ? tradeQty.substring(1) : tradeQty;
        return Long.parseLong(cleaned.trim());
    }

    @Override
    public boolean isConnected() {
        try {
            tokenClient.getAccessToken();
            return true;
        } catch (Exception e) {
            log.warn("키움 접근토큰 발급 실패 — 연결 안 됨으로 보고 (Kiwoom access token issuance failed — reporting disconnected)", e);
            return false;
        }
    }

    private record DailyPriceRequest(
            @JsonProperty("stk_cd") String stockCode,
            @JsonProperty("qry_dt") String queryDate,
            @JsonProperty("indc_tp") String displayType
    ) {
    }

    private record DailyPriceResponse(
            @JsonProperty("daly_stkpc") List<DailyPriceEntry> dalyStkpc
    ) {
    }

    private record DailyPriceEntry(
            @JsonProperty("date") String date,
            @JsonProperty("open_pric") String openPric,
            @JsonProperty("high_pric") String highPric,
            @JsonProperty("low_pric") String lowPric,
            @JsonProperty("close_pric") String closePric,
            @JsonProperty("trde_qty") String tradeQty
    ) {
    }
}
