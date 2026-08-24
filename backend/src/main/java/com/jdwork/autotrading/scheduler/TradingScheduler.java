package com.jdwork.autotrading.scheduler;

import com.jdwork.autotrading.account.AccountService;
import com.jdwork.autotrading.account.domain.AccountSnapshot;
import com.jdwork.autotrading.account.mapper.AccountSnapshotMapper;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.config.TradingModeConfig;
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
    private final TradingModeConfig tradingModeConfig;
    private final KiwoomApiProperties kiwoomApiProperties;
    private final BigDecimal mockInitialCapital;
    private final int staleTradingDays;

    public TradingScheduler(OrderService orderService,
                             StockMasterMapper stockMasterMapper,
                             AccountSnapshotMapper accountSnapshotMapper,
                             OrderLogMapper orderLogMapper,
                             AccountService accountService,
                             TradingModeConfig tradingModeConfig,
                             KiwoomApiProperties kiwoomApiProperties,
                             @Value("${trading.mock.initial-capital}") BigDecimal mockInitialCapital,
                             @Value("${trading.universe.stale-trading-days}") int staleTradingDays) {
        this.orderService = orderService;
        this.stockMasterMapper = stockMasterMapper;
        this.accountSnapshotMapper = accountSnapshotMapper;
        this.orderLogMapper = orderLogMapper;
        this.accountService = accountService;
        this.tradingModeConfig = tradingModeConfig;
        this.kiwoomApiProperties = kiwoomApiProperties;
        this.mockInitialCapital = mockInitialCapital;
        this.staleTradingDays = staleTradingDays;
    }

    /**
     * 장 시작 전: 종목 유니버스 갱신(거래정지 후보 마킹), AI 예측 배치 실행. 08:00 KST.
     * Pre-market: refresh the stock universe (flag suspected halts), run the prediction batch. 08:00 KST.
     *
     * ⚠️ is_trading_halt는 "최근 시세 부재"라는 대리 신호(휴리스틱)로만 갱신한다 — 키움/KRX의 정식
     * 거래정지 통지 연동 전까지의 임시 안전판이다. is_managed(관리종목 지정)는 우리 DB 데이터만으로는
     * 판단할 수 없는 외부 지정 정보라 이 배치에서 다루지 않는다 (KRX/공시 API 연동 TODO).
     * ⚠️ is_trading_halt is refreshed only via a proxy heuristic ("no recent price data") — a stopgap
     * until Kiwoom/KRX's real halt notifications are wired in. is_managed (official "managed stock"
     * designation) can't be derived from our own data, so this batch intentionally leaves it alone
     * (needs a KRX/disclosure API integration, still TODO).
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Seoul")
    public void preMarketJob() {
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
     * 장 마감 후: 당일 체결 정산, 계좌 스냅샷 저장 (BUG-006 수정). 15:40 KST.
     * Post-market: settle the day's fills and save an account snapshot (BUG-006 fix). 15:40 KST.
     *
     * 이 배치가 저장한 스냅샷이 다음 거래일 intradaySignalScan()의 입력이 된다.
     * The snapshot saved here becomes the input for the next trading day's intradaySignalScan().
     *
     * MOCK 모드에서만 계산·저장한다 — LIVE 모드는 실제 계좌 잔고 조회 API 연동 전까지 절대 값을
     * 지어내지 않는다 (Review 필수 반영사항, 설계 §10).
     * Only computed/saved in MOCK mode — LIVE mode never fabricates a value until the real
     * account balance API is wired (Review's must-fix finding, design doc §10).
     */
    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = "Asia/Seoul")
    public void postMarketJob() {
        String accountId = kiwoomApiProperties.getAccountNo();
        String tradingMode = tradingModeConfig.getMode().name();

        if (tradingModeConfig.isLive()) {
            log.warn("실거래 계좌 잔고 조회 API 미연동 — LIVE 모드 계좌 스냅샷 저장을 스킵합니다 "
                    + "(live account balance API not wired yet — skipping LIVE snapshot save)");
            return;
        }

        BigDecimal cumulativeBuys = orderLogMapper.sumFilledValue(tradingMode, OrderLog.OrderType.BUY.name());
        BigDecimal cumulativeSells = orderLogMapper.sumFilledValue(tradingMode, OrderLog.OrderType.SELL.name());
        BigDecimal cashBalance = mockInitialCapital.subtract(cumulativeBuys).add(cumulativeSells);
        BigDecimal positionsValue = accountService.getPositionsValue(accountId, tradingMode);
        BigDecimal totalValue = cashBalance.add(positionsValue);

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

        log.info("장마감 정산 배치 완료: totalValue={}, cashBalance={}, dailyPnlRate={} "
                        + "(post-market settlement complete: totalValue={}, cashBalance={}, dailyPnlRate={})",
                totalValue, cashBalance, dailyPnlRate, totalValue, cashBalance, dailyPnlRate);
    }

    /** 야간: 모델 재학습 배치. 02:00 KST. */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void overnightRetrainJob() {
        // TODO: ML 서비스 재학습 트리거 (FastAPI 배치 엔드포인트 or 별도 잡)
        log.info("야간 모델 재학습 배치 실행 (overnight model retraining job)");
    }
}
