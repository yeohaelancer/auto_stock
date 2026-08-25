package com.jdwork.autotrading.market.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 키움증권 REST API 접근토큰(access token) 발급/캐싱/자동 갱신 클라이언트 (설계 §3.2).
 * Kiwoom REST API access-token issuance/caching/auto-refresh client (design doc §3.2).
 *
 * 요청/응답 필드는 키움 공식 개발자 포털의 "접근토큰발급(au10001)" 문서(2026-08-24 확인, POST /oauth2/token,
 * appkey/secretkey → token/expires_dt/token_type/return_code)를 기준으로 구현했다.
 * Request/response fields are implemented per Kiwoom's official developer portal documentation for
 * "issue access token (au10001)" (verified 2026-08-24, POST /oauth2/token, appkey/secretkey →
 * token/expires_dt/token_type/return_code).
 *
 * ⚠️ 시세/주문 TR은 이 클라이언트의 범위 밖이다 — 별도 검증 필요 (KiwoomMarketRestClient, LiveOrderExecutor 참고).
 * ⚠️ Market-data/order TRs are out of scope here — need separate verification
 *    (see KiwoomMarketRestClient, LiveOrderExecutor).
 */
@Component
public class KiwoomTokenClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomTokenClient.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRES_DT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WebClient webClient;
    private final KiwoomApiProperties properties;

    private volatile CachedToken cachedToken;

    public KiwoomTokenClient(WebClient.Builder builder, KiwoomApiProperties properties) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.properties = properties;
    }

    /**
     * 유효한 접근토큰을 반환한다. 캐시가 없거나 만료 임박(5분 이내)이면 재발급한다.
     * Returns a valid access token, reissuing it if none is cached or it's about to expire (within 5 minutes).
     */
    public synchronized String getAccessToken() {
        if (cachedToken == null || cachedToken.isExpiringSoon()) {
            cachedToken = issueToken();
        }
        return cachedToken.token();
    }

    private CachedToken issueToken() {
        TokenResponse response = webClient.post()
                .uri("/oauth2/token")
                .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                .bodyValue(new TokenRequest("client_credentials", properties.getAppKey(), properties.getAppSecret()))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block();

        if (response == null || response.returnCode() != 0) {
            String message = response != null ? response.returnMsg() : "응답 없음 (no response)";
            throw new IllegalStateException("키움 접근토큰 발급 실패: " + message + " (Kiwoom access token issuance failed: " + message + ")");
        }

        log.info("키움 접근토큰 발급 성공, 만료: {} (Kiwoom access token issued, expires: {})",
                response.expiresDt(), response.expiresDt());
        return new CachedToken(response.token(), parseExpiresAt(response.expiresDt()));
    }

    private OffsetDateTime parseExpiresAt(String expiresDt) {
        LocalDateTime local = LocalDateTime.parse(expiresDt, EXPIRES_DT_FORMAT);
        return local.atZone(KST).toOffsetDateTime();
    }

    private record TokenRequest(
            @JsonProperty("grant_type") String grantType,
            @JsonProperty("appkey") String appKey,
            @JsonProperty("secretkey") String secretKey
    ) {
    }

    private record TokenResponse(
            @JsonProperty("expires_dt") String expiresDt,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("token") String token,
            @JsonProperty("return_code") int returnCode,
            @JsonProperty("return_msg") String returnMsg
    ) {
    }

    private record CachedToken(String token, OffsetDateTime expiresAt) {
        boolean isExpiringSoon() {
            return OffsetDateTime.now(KST).isAfter(expiresAt.minusMinutes(5));
        }
    }
}
