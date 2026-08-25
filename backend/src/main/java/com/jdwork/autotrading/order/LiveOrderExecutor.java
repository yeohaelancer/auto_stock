package com.jdwork.autotrading.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.market.auth.KiwoomTokenClient;
import com.jdwork.autotrading.order.domain.OrderLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 실거래 주문 실행기 — 키움증권 REST API로 실제 주문을 전송한다.
 * Live order executor — sends real orders via the Kiwoom REST API.
 *
 * trading.mode=LIVE일 때만 빈으로 등록되며, 이 클래스가 호출되는 것 자체가 실제 자금이 움직인다는 뜻이다.
 * Only registered when trading.mode=LIVE. Invoking this class means real money moves.
 *
 * ✅ 주식 매수주문(TR: kt10000)·매도주문(TR: kt10001) 모두 키움 공식 개발자 포털 문서로 확인 후 구현했다
 *    (2026-08-24, 사용자가 포털에서 직접 확인한 요청/응답 스펙 기준). POST /api/dostk/ordr.
 *    두 TR은 요청/응답 필드 구조가 완전히 동일하고 TR코드만 다름을 확인함 (당초 가정했던 대로).
 * ✅ Both buy (TR: kt10000) and sell (TR: kt10001) orders implemented per the Kiwoom official developer
 *    portal spec (verified 2026-08-24 from screenshots the user pulled from the portal).
 *    POST /api/dostk/ordr. Confirmed both TRs share an identical field structure, differing only in
 *    TR code (as originally assumed).
 *
 * ⚠️ 매매구분(trde_tp)은 "0"(보통/지정가)로 고정 — 이 시스템은 항상 OrderService가 산정한 지정가로
 *    주문하므로 시장가 등 다른 주문유형은 다루지 않는다.
 * ⚠️ Order method (trde_tp) is fixed to "0" (limit order) — this system always orders at the price
 *    OrderService computed, so market orders and other types are out of scope.
 *
 * ⚠️ 주문 접수(ord_no 수신)만 확인하며, 실제 체결 여부는 별도 체결내역 조회 TR(미검증)로 확인해야 한다.
 *    임의로 체결(FILLED) 상태로 단정하지 않고 PENDING으로 남긴다 (설계 §10).
 * ⚠️ Only confirms order acceptance (receiving ord_no) — actual fill status requires a separate
 *    fill-inquiry TR (not yet verified). Never assumes FILLED; stays PENDING (design doc §10).
 */
@Component
@ConditionalOnProperty(name = "trading.mode", havingValue = "LIVE")
public class LiveOrderExecutor implements OrderExecutor {

    private static final Logger log = LoggerFactory.getLogger(LiveOrderExecutor.class);

    private static final String BUY_TR = "kt10000";
    private static final String SELL_TR = "kt10001";
    private static final String ORDER_PATH = "/api/dostk/ordr";
    private static final String LIMIT_ORDER_TYPE = "0"; // trde_tp: 0=보통(지정가)
    private static final String DOMESTIC_EXCHANGE = "KRX"; // dmst_stex_tp 기본값 — NXT/SOR 미지원 범위

    private final WebClient webClient;
    private final KiwoomTokenClient tokenClient;
    private final KiwoomApiProperties kiwoomApiProperties;

    public LiveOrderExecutor(WebClient.Builder builder, KiwoomApiProperties kiwoomApiProperties, KiwoomTokenClient tokenClient) {
        this.webClient = builder.baseUrl(kiwoomApiProperties.getBaseUrl()).build();
        this.kiwoomApiProperties = kiwoomApiProperties;
        this.tokenClient = tokenClient;
    }

    @Override
    public OrderLog execute(OrderLog order) {
        String trCode = order.getOrderType() == OrderLog.OrderType.BUY ? BUY_TR : SELL_TR;
        log.warn("[LIVE] 실거래 주문 실행: {} {} {}주 (account={})",
                order.getStockCode(), order.getOrderType(), order.getQuantity(), kiwoomApiProperties.getAccountNo());

        try {
            OrderResponse response = webClient.post()
                    .uri(ORDER_PATH)
                    .header("authorization", "Bearer " + tokenClient.getAccessToken())
                    .header("api-id", trCode)
                    .contentType(MediaType.valueOf("application/json;charset=UTF-8"))
                    .bodyValue(new OrderRequest(
                            DOMESTIC_EXCHANGE,
                            order.getStockCode(),
                            String.valueOf(order.getQuantity()),
                            order.getOrderPrice().toPlainString(),
                            LIMIT_ORDER_TYPE))
                    .retrieve()
                    .bodyToMono(OrderResponse.class)
                    .block();

            if (response == null || response.ordNo() == null || response.ordNo().isBlank()) {
                log.error("[LIVE] 주문 접수 실패 — 응답에 주문번호 없음 (order submission failed — no order number in response)");
                order.setExecutionStatus(OrderLog.ExecutionStatus.REJECTED);
                return order;
            }

            order.setKiwoomOrderNo(response.ordNo());
            order.setExecutionStatus(OrderLog.ExecutionStatus.PENDING);
            log.warn("[LIVE] 주문 접수됨(미체결) — 주문번호: {} (order accepted, awaiting fill — order no: {})",
                    response.ordNo(), response.ordNo());
            return order;
        } catch (Exception e) {
            // 주문 실패 시 절대 임의로 체결/성공 처리하지 않는다 (설계 §10)
            // Never treat a failed order as filled/successful (design doc §10)
            log.error("[LIVE] 주문 실행 실패 — {} {} {}주", order.getStockCode(), order.getOrderType(), order.getQuantity(), e);
            order.setExecutionStatus(OrderLog.ExecutionStatus.REJECTED);
            return order;
        }
    }

    private record OrderRequest(
            @JsonProperty("dmst_stex_tp") String exchange,
            @JsonProperty("stk_cd") String stockCode,
            @JsonProperty("ord_qty") String quantity,
            @JsonProperty("ord_uv") String orderPrice,
            @JsonProperty("trde_tp") String orderMethodType
    ) {
    }

    private record OrderResponse(
            @JsonProperty("ord_no") String ordNo,
            @JsonProperty("dmst_stex_tp") String exchange
    ) {
    }
}
