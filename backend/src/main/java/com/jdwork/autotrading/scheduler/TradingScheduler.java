package com.jdwork.autotrading.scheduler;

import com.jdwork.autotrading.account.AccountService;
import com.jdwork.autotrading.account.KiwoomBalanceClient;
import com.jdwork.autotrading.account.KiwoomCashBalanceClient;
import com.jdwork.autotrading.account.domain.AccountSnapshot;
import com.jdwork.autotrading.account.domain.Position;
import com.jdwork.autotrading.account.mapper.AccountSnapshotMapper;
import com.jdwork.autotrading.account.mapper.PositionMapper;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.config.TradingModeConfig;
import com.jdwork.autotrading.market.FeatureEngineeringService;
import com.jdwork.autotrading.market.KiwoomMarketClient;
import com.jdwork.autotrading.market.UniverseSelectionService;
import com.jdwork.autotrading.market.dto.PriceBar;
import com.jdwork.autotrading.market.mapper.PriceHistoryMapper;
import com.jdwork.autotrading.order.KiwoomFillInquiryClient;
import com.jdwork.autotrading.order.OrderService;
import com.jdwork.autotrading.order.domain.OrderLog;
import com.jdwork.autotrading.order.mapper.OrderLogMapper;
import com.jdwork.autotrading.stock.mapper.StockMasterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 장전/장중/장마감/야간 배치 스케줄러 (설계 §8.1).
 * Pre-market / intraday / post-market / overnight batch scheduler (design doc §8.1).
 */
@Component
public class TradingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TradingScheduler.class);

    private final OrderService orderService;
    private final StockMasterMapper stockMasterMapper;
    private final AccountSnapshotMapper accountSnapshotMapper;
    private final OrderLogMapper orderLogMapper;
    private final AccountService accountService;
    private final KiwoomFillInquiryClient fillInquiryClient;
    private final KiwoomBalanceClient balanceClient;
    private final KiwoomCashBalanceClient cashBalanceClient;
    private final PositionMapper positionMapper;
    private final KiwoomMarketClient marketClient;
    private final PriceHistoryMapper priceHistoryMapper;
    private final FeatureEngineeringService featureEngineeringService;
    private final UniverseSelectionService universeSelectionService;
    private final TradingModeConfig tradingModeConfig;
    private final KiwoomApiProperties kiwoomApiProperties;
    private final BigDecimal mockInitialCapital;
    private final int staleTradingDays;

    public TradingScheduler(OrderService orderService,
                             StockMasterMapper stockMasterMapper,
                             AccountSnapshotMapper accountSnapshotMapper,
                             OrderLogMapper orderLogMapper,
                             AccountService accountService,
                             KiwoomFillInquiryClient fillInquiryClient,
                             KiwoomBalanceClient balanceClient,
                             KiwoomCashBalanceClient cashBalanceClient,
                             PositionMapper positionMapper,
                             KiwoomMarketClient marketClient,
                             PriceHistoryMapper priceHistoryMapper,
                             FeatureEngineeringService featureEngineeringService,
                             UniverseSelectionService universeSelectionService,
                             TradingModeConfig tradingModeConfig,
                             KiwoomApiProperties kiwoomApiProperties,
                             @Value("${trading.mock.initial-capital}") BigDecimal mockInitialCapital,
                             @Value("${trading.universe.stale-trading-days}") int staleTradingDays) {
        this.orderService = orderService;
        this.stockMasterMapper = stockMasterMapper;
        this.accountSnapshotMapper = accountSnapshotMapper;
        this.orderLogMapper = orderLogMapper;
        this.accountService = accountService;
        this.fillInquiryClient = fillInquiryClient;
        this.balanceClient = balanceClient;
        this.cashBalanceClient = cashBalanceClient;
        this.positionMapper = positionMapper;
        this.marketClient = marketClient;
        this.priceHistoryMapper = priceHistoryMapper;
        this.featureEngineeringService = featureEngineeringService;
        this.universeSelectionService = universeSelectionService;
        this.tradingModeConfig = tradingModeConfig;
        this.kiwoomApiProperties = kiwoomApiProperties;
        this.mockInitialCapital = mockInitialCapital;
        this.staleTradingDays = staleTradingDays;
    }

    /**
     * 장 시작 전: 매매 유니버스 자동선정, 거래정지 후보 마킹. 08:00 KST.
     * Pre-market: auto-select the trading universe, flag suspected halts. 08:00 KST.
     *
     * 유니버스 자동선정(사용자 요청): 코스피+코스닥 거래대금 상위를 조회해 stock_master를 자동으로 채운다.
     * 더 이상 사람이 종목을 직접 등록할 필요가 없다 — 이 배치가 매일 아침 순위를 다시 매겨 갱신한다.
     * Universe auto-selection (per user request): fetches top-trading-value KOSPI+KOSDAQ stocks and
     * populates stock_master automatically. No human needs to register stocks by hand anymore — this
     * batch re-ranks and refreshes it every morning.
     *
     * ⚠️ is_trading_halt는 "최근 시세 부재"라는 대리 신호(휴리스틱)로만 갱신한다 — 키움/KRX의 정식
     * 거래정지 통지 연동 전까지의 임시 안전판이다. is_managed(관리종목 지정)는 요청 시점에 이미 TR에서
     * 제외(mang_stk_incls=0)되므로 별도 갱신 불필요.
     * ⚠️ is_trading_halt is refreshed only via a proxy heuristic ("no recent price data") — a stopgap
     * until Kiwoom/KRX's real halt notifications are wired in. is_managed is already excluded at the
     * TR request level (mang_stk_incls=0), so no separate refresh is needed for it.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Seoul")
    public void preMarketJob() {
        universeSelectionService.refreshAutoUniverse();

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(staleTradingDays);
        int haltedCount = stockMasterMapper.markStaleAsHalted(cutoff);
        int resumedCount = stockMasterMapper.markActiveAsResumed(cutoff);
        log.info("장전 배치 완료: 거래정지 후보 {}건 추가, {}건 해제 (cutoff={}) "
                        + "(pre-market job complete: {} newly flagged halted, {} resumed, cutoff={})",
                haltedCount, resumedCount, cutoff, haltedCount, resumedCount, cutoff);
        // TODO: AI 예측 배치(장 시작 전 일괄 추론) 트리거 연동 — 현재는 intradaySignalScan()이 매 사이클 개별 호출
    }

    /**
     * 장중: 종목 유니버스를 순회하며 신호→리스크→주문 파이프라인을 자동 실행한다 (BUG-005 수정, 설계 §8.1).
     * Intraday: iterates the stock universe and automatically runs the signal→risk→order pipeline
     * (BUG-005 fix, design doc §8.1).
     *
     * 09:05~14:55 KST 사이 5분 간격 실행. 장 시작 직후와 마감 30여 분 전(변동성이 큰 구간)은 여유를 두고 제외했다.
     * Runs every 5 minutes between 09:05–14:55 KST — deliberately excludes the volatile minutes
     * right at market open and the last ~30 minutes before close.
     */
    @Scheduled(cron = "0 5-59/5 9-14 * * MON-FRI", zone = "Asia/Seoul")
    public void intradaySignalScan() {
        String accountId = kiwoomApiProperties.getAccountNo();
        String tradingMode = tradingModeConfig.getMode().name();

        // 계좌 스냅샷이 없으면 현금/손실률을 알 수 없으므로 이번 사이클은 통째로 스킵한다.
        // 임의값으로 대체하지 않는다 (설계 §10, Review 사전 점검 지적사항).
        // Without a snapshot we don't know cash/loss rate, so skip this whole cycle —
        // never substitute a fabricated value (design doc §10, per Review's pre-check finding).
        AccountSnapshot latest = accountSnapshotMapper.findLatest(accountId, tradingMode);
        if (latest == null) {
            log.warn("계좌 스냅샷이 없어 이번 장중 스캔을 스킵합니다 (no account snapshot yet — skipping this intraday scan cycle)");
            return;
        }

        List<String> universe = stockMasterMapper.findActiveUniverse();
        log.info("장중 신호 스캔 시작: {}종목 (intraday signal scan starting for {} stocks)", universe.size(), universe.size());

        for (String stockCode : universe) {
            try {
                orderService.processSignal(stockCode, latest.getCashBalance(), latest.getDailyPnlRate());
            } catch (Exception e) {
                // 한 종목의 실패가 나머지 종목 처리를 막지 않도록 격리한다 (Review 필수 반영 사항).
                // Isolate failures so one stock's error never blocks the rest of the scan (Review's must-fix item).
                log.error("{} 종목 신호 처리 중 오류 — 다음 종목으로 계속 진행 ({} signal processing failed, continuing with next stock)",
                        stockCode, stockCode, e);
            }
        }
    }

    /**
     * 장중: LIVE 모드의 미체결(PENDING/PARTIAL) 주문을 체결요청(ka10076) TR로 확인해 상태를 갱신한다.
     * Intraday: checks LIVE-mode unresolved (PENDING/PARTIAL) orders via the fill-inquiry TR (ka10076)
     * and updates their status.
     *
     * `LiveOrderExecutor`는 주문 접수만 확인하고 항상 PENDING으로 남기므로, 이 배치가 실제 체결 여부를
     * 확정하는 유일한 경로다. 09:01~15:29 KST 사이 1분 간격 — intradaySignalScan보다 촘촘하게 돈다
     * (체결은 신호 스캔 주기보다 빠르게 일어날 수 있으므로).
     * `LiveOrderExecutor` only confirms order acceptance and always leaves PENDING, so this batch is
     * the only path that actually confirms a fill. Runs every minute between 09:01–15:29 KST — tighter
     * than intradaySignalScan since fills can happen faster than the signal-scan cadence.
     */
    @Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void checkPendingFills() {
        if (!tradingModeConfig.isLive()) {
            return; // MOCK 모드는 즉시 체결 시뮬레이션이라 확인할 미체결 주문이 없음 (no unresolved orders to check in MOCK mode)
        }

        String tradingMode = tradingModeConfig.getMode().name();
        List<OrderLog> unresolved = orderLogMapper.findUnresolvedLiveOrders(tradingMode);
        if (unresolved.isEmpty()) {
            return;
        }

        log.info("미체결 주문 {}건 체결 확인 시작 (checking fill status for {} unresolved orders)",
                unresolved.size(), unresolved.size());

        for (OrderLog order : unresolved) {
            try {
                fillInquiryClient.checkFillStatus(order.getStockCode(), order.getKiwoomOrderNo())
                        .ifPresent(fillStatus -> {
                            if (fillStatus.fullyFilled()) {
                                orderLogMapper.updateFillStatus(order.getOrderId(),
                                        OrderLog.ExecutionStatus.FILLED.name(), fillStatus.filledPrice());
                                log.info("주문 체결 확인: {} (order confirmed filled: {})",
                                        order.getOrderId(), order.getOrderId());
                            } else if (fillStatus.partiallyFilled()) {
                                orderLogMapper.updateFillStatus(order.getOrderId(),
                                        OrderLog.ExecutionStatus.PARTIAL.name(), fillStatus.filledPrice());
                            }
                            // 미체결(접수/확인) 상태면 갱신하지 않고 다음 사이클에 재확인 (still unfilled — leave as-is, recheck next cycle)
                        });
            } catch (Exception e) {
                // 한 주문의 조회 실패가 나머지 주문 확인을 막지 않도록 격리 (같은 원칙, TradingScheduler 전반에 적용)
                // Isolate failures so one order's lookup error never blocks checking the rest (same principle used throughout this class)
                log.error("주문 {} 체결 확인 실패 — 다음 주문으로 계속 진행 (fill-check failed for order {}, continuing)",
                        order.getOrderId(), order.getOrderId(), e);
            }
        }
    }

    /**
     * 장 마감 후: 당일 체결 정산, 계좌 스냅샷 저장 (BUG-006 수정, LIVE 모드는 kt00018 연동으로 확장). 15:40 KST.
     * Post-market: settle the day's fills and save an account snapshot (BUG-006 fix; LIVE mode now
     * wired via TR kt00018). 15:40 KST.
     *
     * 이 배치가 저장한 스냅샷이 다음 거래일 intradaySignalScan()의 입력이 된다.
     * The snapshot saved here becomes the input for the next trading day's intradaySignalScan().
     */
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void postMarketJob() {
        if (tradingModeConfig.isLive()) {
            settleLiveSnapshot();
        } else {
            settleMockSnapshot();
        }
    }

    /**
     * LIVE 모드: 예수금(kt00001) + 계좌평가잔고내역(kt00018)을 조합해 실제 잔고/보유종목을 조회·저장한다.
     * LIVE mode: combines cash balance (kt00001) + account holdings valuation (kt00018) to fetch and
     * save the real balance/positions.
     *
     * 두 TR 중 하나라도 조회에 실패하면 절대 부분적인/추정된 값으로 스냅샷을 저장하지 않는다 (설계 §10).
     * If either TR lookup fails, this never saves a snapshot with a partial/estimated value (design doc §10).
     */
    private void settleLiveSnapshot() {
        String accountId = kiwoomApiProperties.getAccountNo();
        String tradingMode = tradingModeConfig.getMode().name();

        Optional<BigDecimal> cashBalanceOpt = cashBalanceClient.fetchCashBalance();
        Optional<KiwoomBalanceClient.AccountBalance> balanceOpt = balanceClient.fetchAccountBalance();

        if (cashBalanceOpt.isEmpty() || balanceOpt.isEmpty()) {
            log.error("[LIVE] 계좌 잔고 조회 실패 — 이번 장마감 스냅샷 저장을 스킵합니다 "
                    + "([LIVE] account balance lookup failed — skipping this post-market snapshot save)");
            return;
        }

        BigDecimal cashBalance = cashBalanceOpt.get();
        KiwoomBalanceClient.AccountBalance balance = balanceOpt.get();
        BigDecimal totalValue = cashBalance.add(balance.positionsValue());

        for (KiwoomBalanceClient.PositionSnapshot holding : balance.positions()) {
            Position position = new Position();
            position.setAccountId(accountId);
            position.setTradingMode(tradingMode);
            position.setStockCode(holding.stockCode());
            position.setQuantity(holding.quantity());
            position.setAvgPrice(holding.avgPrice());
            positionMapper.upsertFromBalance(position);
        }

        saveSnapshot(accountId, tradingMode, totalValue, cashBalance);
        log.info("[LIVE] 장마감 정산 배치 완료: totalValue={}, cashBalance={}, 보유종목 {}건 "
                        + "([LIVE] post-market settlement complete: totalValue={}, cashBalance={}, {} holdings)",
                totalValue, cashBalance, balance.positions().size(),
                totalValue, cashBalance, balance.positions().size());
    }

    /**
     * MOCK 모드: "초기 시드머니 − 누적매수 + 누적매도"로 현금 잔고를 정확히 역산한다 (BUG-006).
     * MOCK mode: derives cash balance exactly as "initial seed capital − cumulative buys + cumulative sells" (BUG-006).
     */
    private void settleMockSnapshot() {
        String accountId = kiwoomApiProperties.getAccountNo();
        String tradingMode = tradingModeConfig.getMode().name();

        BigDecimal cumulativeBuys = orderLogMapper.sumFilledValue(tradingMode, OrderLog.OrderType.BUY.name());
        BigDecimal cumulativeSells = orderLogMapper.sumFilledValue(tradingMode, OrderLog.OrderType.SELL.name());
        BigDecimal cashBalance = mockInitialCapital.subtract(cumulativeBuys).add(cumulativeSells);
        BigDecimal positionsValue = accountService.getPositionsValue(accountId, tradingMode);
        BigDecimal totalValue = cashBalance.add(positionsValue);

        saveSnapshot(accountId, tradingMode, totalValue, cashBalance);
        log.info("[MOCK] 장마감 정산 배치 완료: totalValue={}, cashBalance={} "
                        + "([MOCK] post-market settlement complete: totalValue={}, cashBalance={})",
                totalValue, cashBalance, totalValue, cashBalance);
    }

    private void saveSnapshot(String accountId, String tradingMode, BigDecimal totalValue, BigDecimal cashBalance) {
        AccountSnapshot previous = accountSnapshotMapper.findLatest(accountId, tradingMode);
        BigDecimal dailyPnl = previous == null ? BigDecimal.ZERO : totalValue.subtract(previous.getTotalValue());
        BigDecimal dailyPnlRate = (previous == null || previous.getTotalValue().signum() <= 0)
                ? BigDecimal.ZERO
                : dailyPnl.divide(previous.getTotalValue(), 6, java.math.RoundingMode.HALF_UP);

        AccountSnapshot snapshot = new AccountSnapshot();
        snapshot.setAccountId(accountId);
        snapshot.setTradingMode(tradingMode);
        snapshot.setSnapshotDate(LocalDate.now());
        snapshot.setTotalValue(totalValue);
        snapshot.setCashBalance(cashBalance);
        snapshot.setDailyPnl(dailyPnl);
        snapshot.setDailyPnlRate(dailyPnlRate);
        accountSnapshotMapper.upsert(snapshot);
    }

    /**
     * 장마감 후: 종목 유니버스 전체의 일봉 시세를 수집해 price_history에 저장하고, 기술적 지표를
     * 계산해 feature_daily에 저장한다 (AI 모델 실데이터 학습의 첫 단계). 16:00 KST.
     * Post-market: collects daily price bars for the whole universe into price_history and computes
     * technical indicators into feature_daily (the first step toward training the AI model on real
     * data). 16:00 KST.
     *
     * postMarketJob(15:40) 이후에 실행해 당일 체결이 반영된 종가를 사용한다.
     * Runs after postMarketJob (15:40) so the day's close reflects the day's actual trading.
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Seoul")
    public void collectPriceHistoryAndFeatures() {
        List<String> universe = stockMasterMapper.findActiveUniverse();
        log.info("시세/피처 수집 배치 시작: {}종목 (price/feature collection starting for {} stocks)",
                universe.size(), universe.size());

        int collected = 0;
        for (String stockCode : universe) {
            try {
                // 키움 TR(ka10086)이 과거 여러 날짜를 한 번에 돌려주므로, 매일 1건씩이 아니라 60일치를
                // 받아 한꺼번에 채운다 — 지표(MA20/RSI14/MACD 등) 계산에 필요한 과거 데이터를 빠르게 축적하기 위함.
                // Kiwoom's TR (ka10086) returns many past days at once, so we pull 60 days per run instead
                // of just 1 — this backfills the history needed for indicators (MA20/RSI14/MACD, etc.) quickly.
                List<PriceBar> bars = marketClient.getRecentPriceBars(stockCode, "DAILY", 60);
                if (bars.isEmpty()) {
                    continue; // 시세 미확보 — 이 종목만 스킵 (no price available — skip just this stock)
                }
                bars.forEach(priceHistoryMapper::upsert);
                featureEngineeringService.computeAndSaveHistory(stockCode);
                collected++;
            } catch (Exception e) {
                // 한 종목의 실패가 나머지 종목 수집을 막지 않도록 격리 (TradingScheduler 전반의 원칙과 동일)
                // Isolate failures so one stock's error never blocks collecting the rest (same principle used throughout this class)
                log.error("{} 종목 시세/피처 수집 실패 — 다음 종목으로 계속 진행 ({} price/feature collection failed, continuing)",
                        stockCode, stockCode, e);
            }
        }
        log.info("시세/피처 수집 배치 완료: {}/{}종목 (price/feature collection complete: {}/{} stocks)",
                collected, universe.size(), collected, universe.size());
    }

    /** 야간: 모델 재학습 배치. 02:00 KST. */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void overnightRetrainJob() {
        // TODO: ML 서비스 재학습 트리거 (FastAPI 배치 엔드포인트 or 별도 잡)
        log.info("야간 모델 재학습 배치 실행 (overnight model retraining job)");
    }
}
