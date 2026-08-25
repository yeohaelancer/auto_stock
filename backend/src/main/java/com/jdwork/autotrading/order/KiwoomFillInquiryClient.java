package com.jdwork.autotrading.order;

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
import java.util.Optional;

/**
 * 키움증권 체결요청 TR(ka10076) 클라이언트 — 주문이 실제로 체결됐는지 확인한다.
 * Kiwoom fill-inquiry TR (ka10076) client — checks whether an order has actually been filled.
 *
 * ✅ 키움 공식 개발자 포털 문서로 확인 후 구현했다 (2026-08-24, 사용자가 포털에서 직접 확인한
 *    요청/응답 스펙 기준). POST /api/dostk/acnt.
 * ✅ Implemented per the Kiwoom official developer portal spec (verified 2026-08-24 from
 *    screenshots the user pulled from the portal). POST /api/dostk/acnt.
 *
 * ⚠️ 요청의 ord_no는 "이 주문번호보다 과거에 체결된 내역"을 뜻하는 페이징 커서이지, 특정 주문 하나를
 *    정확히 짚어 조회하는 필터가 아니다 — 그래서 종목코드로 최근 체결 목록을 받아온 뒤, 그 목록에서
 *    우리 주문번호(kiwoomOrderNo)와 일치하는 항목을 찾는 방식으로 구현했다.
 * ⚠️ The request's ord_no is a pagination cursor ("fills older than this order number"), not an exact
 *    single-order filter — so this fetches the recent fill list for the stock and then finds the entry
 *    matching our order number (kiwoomOrderNo) within that list.
 */
@Component
public class KiwoomFillInquiryClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomFillInquiryClient.class);

    private static final String FILL_INQUIRY_TR = "ka10076";
    private static final String ACCOUNT_PATH = "/api/dostk/acnt";
    private static final String QUERY_TYPE_SINGLE_STOCK = "1"; // qry_tp: 1=종목
    private static final String SELL_TYPE_ALL = "0";           // sell_tp: 0=전체
    private static final String EXCHANGE_TYPE_ALL = "0";        // stex_tp: 0=통합

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;

    public KiwoomFillInquiryClient(WebClient.Builder builder, KiwoomApiProperties properties, KiwoomTokenClient tokenClient) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenClient = tokenClient;
    }

    /** 지정 종목의 최근 체결 목록에서 주문번호가 일치하는 항목을 찾아 상태를 반환한다. 못 찾으면 empty. */
    /** Finds the entry matching the order number in the stock's recent fill list. Empty if not found. */
    public Optional<FillStatus> checkFillStatus(String stockCode, String kiwoomOrderNo) {
        try {
            FillInquiryResponse response = webClient.post()
                    .uri(ACCOUNT_PATH)
                    .header("authorization", "Bearer " + tokenClient.getAccessToken())
                    .header("api-id", FILL_INQUIRY_TR)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                    .bodyValue(new FillInquiryRequest(stockCode, QUERY_TYPE_SINGLE_STOCK, SELL_TYPE_ALL, null, EXCHANGE_TYPE_ALL))
                    .retrieve()
                    .bodyToMono(FillInquiryResponse.class)
                    .block();

            if (response == null || response.cntr() == null) {
                return Optional.empty();
            }

            return response.cntr().stream()
                    .filter(entry -> kiwoomOrderNo.equals(entry.ordNo()))
                    .findFirst()
                    .map(this::toFillStatus);
        } catch (Exception e) {
            log.error("체결 확인 조회 실패 — {} 종목, 주문번호 {} (fill-check lookup failed for stock {}, order {})",
                    stockCode, kiwoomOrderNo, stockCode, kiwoomOrderNo, e);
            return Optional.empty(); // 조회 실패 시 상태를 임의로 단정하지 않음 (never guess a status on failure)
        }
    }

    private FillStatus toFillStatus(FillEntry entry) {
        int orderedQty = parseIntSafely(entry.ordQty());
        int filledQty = parseIntSafely(entry.cntrQty());
        BigDecimal filledPrice = entry.cntrPric() != null && !entry.cntrPric().isBlank()
                ? new BigDecimal(entry.cntrPric())
                : null;
        boolean fullyFilled = "체결".equals(entry.ordStt()) && filledQty > 0 && filledQty >= orderedQty;
        boolean partiallyFilled = filledQty > 0 && filledQty < orderedQty;
        return new FillStatus(entry.ordStt(), filledQty, filledPrice, fullyFilled, partiallyFilled);
    }

    private int parseIntSafely(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String cleaned = value.startsWith("+") ? value.substring(1) : value;
        try {
            return Integer.parseInt(cleaned.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 체결 확인 결과. Fill-check result. */
    public record FillStatus(String orderStatus, int filledQuantity, BigDecimal filledPrice,
                              boolean fullyFilled, boolean partiallyFilled) {
    }

    private record FillInquiryRequest(
            @JsonProperty("stk_cd") String stockCode,
            @JsonProperty("qry_tp") String queryType,
            @JsonProperty("sell_tp") String sellType,
            @JsonProperty("ord_no") String orderNoCursor,
            @JsonProperty("stex_tp") String exchangeType
    ) {
    }

    private record FillInquiryResponse(
            @JsonProperty("cntr") List<FillEntry> cntr
    ) {
    }

    private record FillEntry(
            @JsonProperty("ord_no") String ordNo,
            @JsonProperty("ord_pric") String ordPric,
            @JsonProperty("ord_qty") String ordQty,
            @JsonProperty("cntr_pric") String cntrPric,
            @JsonProperty("cntr_qty") String cntrQty,
            @JsonProperty("oso_qty") String osoQty,
            @JsonProperty("ord_stt") String ordStt
    ) {
    }
}
