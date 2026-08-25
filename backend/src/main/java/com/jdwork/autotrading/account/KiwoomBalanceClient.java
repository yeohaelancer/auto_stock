package com.jdwork.autotrading.account;

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
import java.util.Optional;

/**
 * 키움증권 계좌평가잔고내역요청 TR(kt00018) 클라이언트 — 실거래(LIVE) 계좌의 잔고/보유종목을 조회한다.
 * Kiwoom account balance/holdings TR (kt00018) client — fetches LIVE account balance and positions.
 *
 * ✅ 키움 공식 개발자 포털 문서로 확인 후 구현했다 (2026-08-24, 사용자가 포털에서 직접 확인한
 *    요청/응답 스펙 기준). POST /api/dostk/acnt.
 * ✅ Implemented per the Kiwoom official developer portal spec (verified 2026-08-24 from
 *    screenshots the user pulled from the portal). POST /api/dostk/acnt.
 *
 * 이 TR은 보유종목 평가액(`tot_evlt_amt`)과 종목별 보유 내역만 제공한다. 현금 잔고(예수금)는 이
 * TR에 명시된 필드가 없어, 별도 TR(kt00001, 예수금상세현황요청)을 제공하는 KiwoomCashBalanceClient가
 * 전담한다 — 이전에 이 클래스에서 시도했던 "추정예탁자산 − 총평가금액" 역산 추론은 kt00001의
 * 명시적 `entr`(예수금) 필드 확인 후 폐기했다.
 * This TR only provides position valuation (`tot_evlt_amt`) and per-stock holdings. Cash balance
 * (deposit) has no field here — that's now handled exclusively by KiwoomCashBalanceClient via the
 * dedicated kt00001 TR. An earlier inferred derivation attempted in this class ("estimated assets
 * minus position valuation") was dropped once kt00001's explicit `entr` (deposit) field was confirmed.
 */
@Component
public class KiwoomBalanceClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomBalanceClient.class);

    private static final String BALANCE_TR = "kt00018";
    private static final String ACCOUNT_PATH = "/api/dostk/acnt";
    private static final String AGGREGATED_QUERY = "1"; // qry_tp: 1=합산
    private static final String EXCHANGE_KRX = "KRX";

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;

    public KiwoomBalanceClient(WebClient.Builder builder, KiwoomApiProperties properties, KiwoomTokenClient tokenClient) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenClient = tokenClient;
    }

    public Optional<AccountBalance> fetchAccountBalance() {
        try {
            BalanceResponse response = webClient.post()
                    .uri(ACCOUNT_PATH)
                    .header("authorization", "Bearer " + tokenClient.getAccessToken())
                    .header("api-id", BALANCE_TR)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                    .bodyValue(new BalanceRequest(AGGREGATED_QUERY, EXCHANGE_KRX))
                    .retrieve()
                    .bodyToMono(BalanceResponse.class)
                    .block();

            if (response == null) {
                return Optional.empty();
            }

            BigDecimal positionsValue = parseAmount(response.totalEvaluationAmount());

            List<PositionSnapshot> positions = response.positions() == null
                    ? List.of()
                    : response.positions().stream()
                            .map(this::toPositionSnapshot)
                            .filter(Objects::nonNull)
                            .toList();

            return Optional.of(new AccountBalance(positionsValue, positions));
        } catch (Exception e) {
            log.error("키움 계좌 잔고 조회 실패 (Kiwoom account balance lookup failed)", e);
            return Optional.empty(); // 실패 시 절대 임의 값으로 대체하지 않음 (never fabricate a value on failure)
        }
    }

    private PositionSnapshot toPositionSnapshot(PositionEntry entry) {
        try {
            String stockCode = stripExchangePrefix(entry.stockCode());
            int quantity = Integer.parseInt(stripSign(entry.remainingQty()).trim());
            BigDecimal avgPrice = parseAmount(entry.purchasePrice());
            return new PositionSnapshot(stockCode, quantity, avgPrice);
        } catch (Exception e) {
            log.warn("보유종목 응답 항목 파싱 실패 — 해당 항목 스킵 (failed to parse a holding entry, skipping it)", e);
            return null;
        }
    }

    /** "A005930" 형태의 접두어(A:주식/J:ELW/Q:ETN)를 제거해 6자리 종목코드만 남긴다. */
    /** Strips the 1-char exchange/instrument prefix (A:stock/J:ELW/Q:ETN) to leave the 6-digit code. */
    private String stripExchangePrefix(String stockCode) {
        if (stockCode != null && stockCode.length() == 7 && Character.isLetter(stockCode.charAt(0))) {
            return stockCode.substring(1);
        }
        return stockCode;
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(stripSign(value).trim());
    }

    private String stripSign(String value) {
        return value.startsWith("+") ? value.substring(1) : value;
    }

    /** LIVE 계좌 보유종목 평가 조회 결과 (현금은 KiwoomCashBalanceClient 담당). */
    /** LIVE account holdings valuation result (cash is handled by KiwoomCashBalanceClient). */
    public record AccountBalance(BigDecimal positionsValue, List<PositionSnapshot> positions) {
    }

    /** 보유 종목 스냅샷 (계좌/모드는 호출부가 채운다). Holding snapshot (account/mode filled in by the caller). */
    public record PositionSnapshot(String stockCode, int quantity, BigDecimal avgPrice) {
    }

    private record BalanceRequest(
            @JsonProperty("qry_tp") String queryType,
            @JsonProperty("dmst_stex_tp") String exchangeType
    ) {
    }

    private record BalanceResponse(
            @JsonProperty("tot_evlt_amt") String totalEvaluationAmount,
            @JsonProperty("acnt_evlt_remn_indv_tot") List<PositionEntry> positions
    ) {
    }

    private record PositionEntry(
            @JsonProperty("stk_cd") String stockCode,
            @JsonProperty("rmnd_qty") String remainingQty,
            @JsonProperty("pur_pric") String purchasePrice
    ) {
    }
}
