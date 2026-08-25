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
import java.util.Optional;

/**
 * 키움증권 예수금상세현황요청 TR(kt00001) 클라이언트 — 실계좌의 실제 현금 잔고(예수금)를 조회한다.
 * Kiwoom deposit/cash-balance detail TR (kt00001) client — fetches the real account's actual cash balance.
 *
 * ✅ 키움 공식 개발자 포털 문서로 확인 후 구현했다 (2026-08-24, 사용자가 포털에서 직접 확인한
 *    요청/응답 스펙 기준). POST /api/dostk/acnt.
 * ✅ Implemented per the Kiwoom official developer portal spec (verified 2026-08-24 from
 *    screenshots the user pulled from the portal). POST /api/dostk/acnt.
 *
 * ✅ 응답의 `entr`(예수금) 필드를 그대로 사용한다 — KiwoomBalanceClient(kt00018)가 쓰던
 *    "추정예탁자산 − 총평가금액" 역산 추론을 이 명시적 필드로 대체한다.
 * ✅ Uses the response's `entr` (deposit/cash) field directly — replacing the inferred
 *    "estimated assets minus position valuation" derivation that KiwoomBalanceClient (kt00018) used.
 */
@Component
public class KiwoomCashBalanceClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomCashBalanceClient.class);

    private static final String CASH_BALANCE_TR = "kt00001";
    private static final String ACCOUNT_PATH = "/api/dostk/acnt";
    private static final String GENERAL_QUERY = "2"; // qry_tp: 2=일반조회 (현재 예수금)

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;

    public KiwoomCashBalanceClient(WebClient.Builder builder, KiwoomApiProperties properties, KiwoomTokenClient tokenClient) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenClient = tokenClient;
    }

    public Optional<BigDecimal> fetchCashBalance() {
        try {
            CashBalanceResponse response = webClient.post()
                    .uri(ACCOUNT_PATH)
                    .header("authorization", "Bearer " + tokenClient.getAccessToken())
                    .header("api-id", CASH_BALANCE_TR)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                    .bodyValue(new CashBalanceRequest(GENERAL_QUERY))
                    .retrieve()
                    .bodyToMono(CashBalanceResponse.class)
                    .block();

            if (response == null || response.deposit() == null || response.deposit().isBlank()) {
                return Optional.empty();
            }

            String cleaned = response.deposit().startsWith("+") ? response.deposit().substring(1) : response.deposit();
            return Optional.of(new BigDecimal(cleaned.trim()));
        } catch (Exception e) {
            log.error("키움 예수금 조회 실패 (Kiwoom cash balance lookup failed)", e);
            return Optional.empty(); // 실패 시 절대 임의 값으로 대체하지 않음 (never fabricate a value on failure)
        }
    }

    private record CashBalanceRequest(@JsonProperty("qry_tp") String queryType) {
    }

    private record CashBalanceResponse(@JsonProperty("entr") String deposit) {
    }
}
