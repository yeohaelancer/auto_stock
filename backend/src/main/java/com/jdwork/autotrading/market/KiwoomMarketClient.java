package com.jdwork.autotrading.market;

import com.jdwork.autotrading.market.dto.PriceBar;

import java.util.List;

/**
 * 키움증권 REST API 시세 조회 클라이언트 인터페이스.
 * Kiwoom REST API market data client interface.
 *
 * 구현체는 인증 토큰 발급/갱신(§3.2), TR 호출 제한 대응 레이트 리미터(§3.4)를 내부에서 처리한다.
 * Implementations handle token issuance/refresh (design doc §3.2) and TR rate limiting (§3.4) internally.
 *
 * NOTE: 실제 키움 REST API 엔드포인트/TR 코드는 공식 개발자 문서로 최종 확인 후 구현할 것.
 * NOTE: Actual Kiwoom REST API endpoints/TR codes must be verified against official docs before implementation.
 */
public interface KiwoomMarketClient {

    /** 지정 종목의 최근 시세 봉 목록을 조회한다. Fetch recent price bars for the given stock. */
    List<PriceBar> getRecentPriceBars(String stockCode, String intervalType, int count);

    /** 접속 상태(토큰 유효/WebSocket 연결 여부)를 확인한다. Check connection/token health. */
    boolean isConnected();
}
