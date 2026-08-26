package com.jdwork.autotrading.api;

import com.jdwork.autotrading.account.domain.AccountSnapshot;
import com.jdwork.autotrading.account.mapper.AccountSnapshotMapper;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.market.dto.CollectionStatus;
import com.jdwork.autotrading.market.mapper.MarketStatusMapper;
import com.jdwork.autotrading.order.domain.OrderLog;
import com.jdwork.autotrading.order.mapper.OrderLogMapper;
import com.jdwork.autotrading.risk.domain.RiskEvent;
import com.jdwork.autotrading.risk.mapper.RiskEventMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 프론트엔드 모니터링 화면용 조회 전용 REST 컨트롤러 — 주문 이력/리스크 이벤트/계좌 스냅샷 추이/
 * 시세·피처 수집 현황을 노출한다. 상태 변경 없음(전부 GET) — 실제 매매/설정 변경은 TradingController가 담당.
 * Read-only REST controller backing the frontend monitoring views — exposes order history, risk
 * events, the account snapshot trend, and price/feature collection status. All GET, no mutation —
 * trading actions/config changes remain TradingController's responsibility.
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final OrderLogMapper orderLogMapper;
    private final RiskEventMapper riskEventMapper;
    private final AccountSnapshotMapper accountSnapshotMapper;
    private final MarketStatusMapper marketStatusMapper;
    private final KiwoomApiProperties kiwoomApiProperties;

    public MonitoringController(OrderLogMapper orderLogMapper,
                                 RiskEventMapper riskEventMapper,
                                 AccountSnapshotMapper accountSnapshotMapper,
                                 MarketStatusMapper marketStatusMapper,
                                 KiwoomApiProperties kiwoomApiProperties) {
        this.orderLogMapper = orderLogMapper;
        this.riskEventMapper = riskEventMapper;
        this.accountSnapshotMapper = accountSnapshotMapper;
        this.marketStatusMapper = marketStatusMapper;
        this.kiwoomApiProperties = kiwoomApiProperties;
    }

    /** 최근 주문/체결 이력 — OrderHistoryPanel 컴포넌트용. */
    @GetMapping("/orders")
    public List<OrderLog> recentOrders(@RequestParam String tradingMode,
                                        @RequestParam(defaultValue = "50") int limit) {
        return orderLogMapper.findRecentByMode(tradingMode, limit);
    }

    /** 최근 리스크 이벤트 — RiskEventLogPanel 컴포넌트용. */
    @GetMapping("/risk-events")
    public List<RiskEvent> recentRiskEvents(@RequestParam String tradingMode,
                                             @RequestParam(defaultValue = "50") int limit) {
        return riskEventMapper.findRecentByMode(tradingMode, limit);
    }

    /** 계좌 스냅샷(평가금액/현금/손익) 추이 — AccountSnapshotPanel 컴포넌트용. */
    @GetMapping("/account-snapshots")
    public List<AccountSnapshot> accountSnapshotHistory(@RequestParam String tradingMode,
                                                          @RequestParam(defaultValue = "30") int limit) {
        return accountSnapshotMapper.findHistory(kiwoomApiProperties.getAccountNo(), tradingMode, limit);
    }

    /** 시세/피처 수집 현황 + 매매 유니버스 크기 — CollectionStatusPanel 컴포넌트용. */
    @GetMapping("/collection-status")
    public CollectionStatus collectionStatus() {
        return marketStatusMapper.findCollectionStatus();
    }
}
