package com.jdwork.autotrading.market;

import com.jdwork.autotrading.market.dto.PriceBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 키움증권 REST API 시세 클라이언트 골격 구현체.
 * Skeleton implementation of the Kiwoom REST API market data client.
 *
 * TODO: 인증 토큰 발급/갱신(§3.2), 실제 시세 조회 TR 연동. 그 전까지는 항상 "미연결" 상태로
 * 정직하게 보고한다 — 연결된 것처럼 거짓 응답하면 시세 없이 주문이 나가는 사고로 이어질 수 있다.
 * TODO: wire up token issuance/refresh (design doc §3.2) and the real market data TR calls.
 * Until then this honestly reports "not connected" — falsely claiming connectivity could let an
 * order go out with no real price behind it.
 */
@Component
public class KiwoomMarketRestClient implements KiwoomMarketClient {

    private static final Logger log = LoggerFactory.getLogger(KiwoomMarketRestClient.class);

    @Override
    public List<PriceBar> getRecentPriceBars(String stockCode, String intervalType, int count) {
        log.warn("키움 시세 API 미연동 상태 — {} 종목 시세 조회 불가 (Kiwoom market API not wired yet, cannot fetch price for {})",
                stockCode, stockCode);
        return List.of();
    }

    @Override
    public boolean isConnected() {
        return false; // 실제 연동 전까지 항상 미연결로 보고 (fail-safe until the real integration is wired)
    }
}
