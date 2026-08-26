package com.jdwork.autotrading.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.market.auth.KiwoomTokenClient;
import com.jdwork.autotrading.market.dto.PriceBar;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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

    /**
     * 키움 REST TR의 연속조회(pagination) 헤더 — 실제 모의투자 API를 직접 호출해 확인함(2026-08-26):
     * 응답 헤더에 cont-yn(Y/N), next-key, resp-cnt(이번 페이지 건수, 관측상 20건 고정)가 내려오고,
     * 다음 페이지를 받으려면 같은 이름의 요청 헤더로 next-key를 그대로 돌려보내야 한다.
     * 이걸 몰랐을 때는 종목당 항상 최신 20일치만 받아왔다 — count=60을 요청해도 API가 실제로는
     * 20건만 주는데 그걸 그대로 받아들이고 있었던 것 (원래 의도했던 60일 백필이 계속 20일로 조용히
     * 잘려있던 셈).
     * Kiwoom REST TR pagination headers — confirmed by calling the mock API directly (2026-08-26):
     * the response carries cont-yn (Y/N), next-key, and resp-cnt (this page's count, observed fixed at
     * 20); the next page is fetched by echoing next-key back as a request header of the same name.
     * Before this was known, every stock only ever got the latest 20 days — requesting count=60 did
     * nothing because the API only ever returns 20 per call and that was accepted as the full answer
     * (the intended 60-day backfill was silently truncated to 20 all along).
     */
    private static final String CONT_YN_HEADER = "cont-yn";
    private static final String NEXT_KEY_HEADER = "next-key";
    /** 무한루프 방지용 안전판 — count가 아무리 커도 이 페이지 수를 넘기지 않는다. Safety cap against a runaway loop, regardless of how large count is. */
    private static final int MAX_PAGES = 50;

    /**
     * 429 재시도 횟수/대기시간 — 실운영 중 발견: 새 JVM에서 첫 호출 시 접근토큰 발급(KiwoomTokenClient,
     * 이쪽은 별도 레이트리밋이 없음) 직후 곧바로 이 클라이언트의 첫 시세 호출이 나가면서 두 요청이
     * 초당 한도 없이 거의 동시에 나가 429가 나는 사례를 확인. 재시도로 흡수한다(원인을 완전히 없애기보다,
     * 일시적 429는 항상 있을 수 있다고 보고 방어적으로 재시도하는 편이 더 견고함).
     * 429 retry count/backoff — found in real operation: on a fresh JVM's very first call, access-token
     * issuance (KiwoomTokenClient, which has no rate limiting of its own) fires immediately followed by
     * this client's first price request, with no gap enforced between the two — occasionally tripping
     * 429. Retrying absorbs this (more robust than trying to eliminate every possible collision — a
     * transient 429 can always happen for other reasons too).
     */
    private static final int MAX_429_RETRIES = 2;
    private static final long RETRY_BACKOFF_MILLIS = 1500L;

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;
    private final Bucket rateLimiter;

    /**
     * ⚠️ 종목 유니버스를 순회하며 이 클라이언트를 반복 호출하는 배치(collectPriceHistoryAndFeatures 등)가
     * 아무 제한 없이 연속 호출하면 429 Too Many Requests가 발생하는 것을 실제 운영 중 확인 — 종목별
     * 호출 사이에 초당 요청 한도를 두어 막는다 (trading.kiwoom.rate-limit-per-second).
     * ⚠️ Observed in real operation: a batch that loops over the stock universe calling this client back
     * to back (e.g. collectPriceHistoryAndFeatures) triggers 429 Too Many Requests with no throttling —
     * gate it with a per-second request cap between per-stock calls (trading.kiwoom.rate-limit-per-second).
     */
    public KiwoomMarketRestClient(WebClient.Builder builder, KiwoomApiProperties properties, KiwoomTokenClient tokenClient,
                                   @Value("${trading.kiwoom.rate-limit-per-second}") int rateLimitPerSecond) {
        this.webClient = builder.baseUrl(properties.getBaseUrl()).build();
        this.tokenClient = tokenClient;
        // 용량을 rateLimitPerSecond가 아니라 1로 둔 이유: capacity=N으로 두면 시작 시 N개가 한꺼번에
        // "버스트" 소모될 수 있어(첫 N종목이 거의 동시에 나감), 실측 결과 초당 4건 한도로도 429가 계속
        // 발생했다. capacity=1 + (1000/N)ms마다 1개 리필로 바꿔 완전히 균등한 간격으로만 나가게 한다.
        // Capacity is set to 1, not rateLimitPerSecond: capacity=N lets N requests burst out nearly at
        // once at startup (the first N stocks fire almost simultaneously) — in practice this still hit
        // 429 even at a 4/sec cap. Using capacity=1 with one refill every (1000/N)ms instead forces
        // strictly evenly-spaced requests with no burst.
        long intervalMillis = Math.max(1L, 1000L / rateLimitPerSecond);
        this.rateLimiter = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(1)
                        .refillGreedy(1, Duration.ofMillis(intervalMillis))
                        .build())
                .build();
    }

    @Override
    public List<PriceBar> getRecentPriceBars(String stockCode, String intervalType, int count) {
        if (!"DAILY".equals(intervalType)) {
            log.warn("분봉(MINUTE) TR 미검증 — {} 종목 조회 스킵 (minute-bar TR not verified yet, skipping {})",
                    stockCode, stockCode);
            return List.of();
        }

        String queryDate = LocalDate.now(KST).format(YYYYMMDD);
        List<PriceBar> collected = new ArrayList<>();
        String contYn = null;
        String nextKey = null;

        pageLoop:
        for (int page = 0; page < MAX_PAGES && collected.size() < count; page++) {
            for (int attempt = 0; attempt <= MAX_429_RETRIES; attempt++) {
                try {
                    rateLimiter.asBlocking().consume(1); // 종목/페이지 호출 사이 초당 요청 한도 대기 (block until under the per-second cap)

                    WebClient.RequestBodySpec request = webClient.post()
                            .uri(DAILY_PRICE_PATH)
                            .header("authorization", "Bearer " + tokenClient.getAccessToken())
                            .header("api-id", DAILY_PRICE_TR)
                            .contentType(MediaType.valueOf("application/json;charset=UTF-8"));
                    if (nextKey != null) {
                        // 첫 페이지는 이 헤더들을 빼고 보낸다 — 이어서 받을 페이지가 없을 때부터만 붙인다
                        // Omit these on the first page — only attach once there's an actual page to continue from
                        request = request.header(CONT_YN_HEADER, "Y").header(NEXT_KEY_HEADER, nextKey);
                    }

                    ResponseEntity<DailyPriceResponse> response = request
                            .bodyValue(new DailyPriceRequest(stockCode, queryDate, "0"))
                            .retrieve()
                            .toEntity(DailyPriceResponse.class)
                            .block();

                    if (response == null || response.getBody() == null || response.getBody().dalyStkpc() == null) {
                        break pageLoop;
                    }

                    response.getBody().dalyStkpc().stream()
                            .map(entry -> toPriceBar(stockCode, entry))
                            .filter(Objects::nonNull)
                            .forEach(collected::add);

                    contYn = response.getHeaders().getFirst(CONT_YN_HEADER);
                    nextKey = response.getHeaders().getFirst(NEXT_KEY_HEADER);
                    break; // 이 페이지 성공 — 재시도 루프 탈출하고 다음 페이지로 (this page succeeded — leave the retry loop, move to the next page)
                } catch (WebClientResponseException.TooManyRequests e) {
                    if (attempt == MAX_429_RETRIES) {
                        log.error("키움 일별주가 조회 실패(429, 재시도 {}회 소진) — {} 종목, {}번째 페이지 "
                                        + "(Kiwoom daily price lookup failed: 429, {} retries exhausted — {}, page {})",
                                MAX_429_RETRIES, stockCode, page, MAX_429_RETRIES, stockCode, page);
                        break pageLoop;
                    }
                    log.warn("429 응답 — {}ms 대기 후 재시도 ({}/{}) — {} 종목, {}번째 페이지 "
                                    + "(429 response — retrying in {}ms ({}/{}) — {}, page {})",
                            RETRY_BACKOFF_MILLIS, attempt + 1, MAX_429_RETRIES, stockCode, page,
                            RETRY_BACKOFF_MILLIS, attempt + 1, MAX_429_RETRIES, stockCode, page);
                    sleepUninterruptibly(RETRY_BACKOFF_MILLIS);
                } catch (Exception e) {
                    // 조회 실패 시 절대 임의 값으로 대체하지 않는다 (설계 §10) — 다만 이전 페이지에서 이미
                    // 실제로 받아온 데이터는 조작된 값이 아니므로 폐기하지 않고 그대로 반환한다.
                    // Never fabricate a value on failure (design doc §10) — but real bars already fetched in
                    // earlier pages aren't fabricated, so they're kept and returned rather than discarded.
                    log.error("키움 일별주가 조회 실패 — {} 종목, {}번째 페이지 (Kiwoom daily price lookup failed for {}, page {})",
                            stockCode, page, stockCode, page, e);
                    break pageLoop;
                }
            }

            if (!"Y".equalsIgnoreCase(contYn) || nextKey == null || nextKey.isBlank()) {
                break; // 더 받을 페이지 없음 (no more pages available)
            }
        }

        return collected.stream()
                .sorted(Comparator.comparing(PriceBar::tradeDateTime).reversed()) // 응답 정렬 순서를 가정하지 않고 직접 최신순 정렬
                .limit(count)
                .toList();
    }

    /** 429 재시도 대기용 — 인터럽트되면 그냥 조기 반환한다(재시도가 한 번 덜 되는 것뿐, 안전). */
    /** Sleep for the 429 retry backoff — on interrupt, just returns early (one fewer retry, harmless). */
    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
