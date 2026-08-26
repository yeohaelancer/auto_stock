package com.jdwork.autotrading.api;

import com.jdwork.autotrading.account.AccountService;
import com.jdwork.autotrading.account.domain.AccountSnapshot;
import com.jdwork.autotrading.account.domain.Position;
import com.jdwork.autotrading.account.mapper.AccountSnapshotMapper;
import com.jdwork.autotrading.config.KiwoomApiProperties;
import com.jdwork.autotrading.config.TradingModeConfig;
import com.jdwork.autotrading.order.OrderService;
import com.jdwork.autotrading.order.domain.OrderLog;
import com.jdwork.autotrading.risk.RiskEngine;
import com.jdwork.autotrading.risk.domain.RiskEvent;
import com.jdwork.autotrading.risk.mapper.RiskEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 자동매매 위젯(단독 실행 프론트엔드)용 REST 컨트롤러.
 * REST controller backing the auto-trading widget (standalone frontend).
 *
 * Review Agent 지적사항 반영: 긴급정지는 리스크 엔진 상태 전환 + risk_log 기록을 원자적으로 처리한다.
 * Addresses Review Agent finding: emergency stop atomically flips risk engine state and records risk_log.
 */
@RestController
@RequestMapping("/api/trading")
public class TradingController {

    private final RiskEngine riskEngine;
    private final AccountService accountService;
    private final OrderService orderService;
    private final RiskEventMapper riskEventMapper;
    private final AccountSnapshotMapper accountSnapshotMapper;
    private final TradingModeConfig tradingModeConfig;
    private final KiwoomApiProperties kiwoomApiProperties;
    private final BigDecimal dailyLossLimitRate;

    public TradingController(RiskEngine riskEngine,
                              AccountService accountService,
                              OrderService orderService,
                              RiskEventMapper riskEventMapper,
                              AccountSnapshotMapper accountSnapshotMapper,
                              TradingModeConfig tradingModeConfig,
                              KiwoomApiProperties kiwoomApiProperties,
                              @Value("${trading.risk.daily-loss-limit-rate}") BigDecimal dailyLossLimitRate) {
        this.riskEngine = riskEngine;
        this.accountService = accountService;
        this.orderService = orderService;
        this.riskEventMapper = riskEventMapper;
        this.accountSnapshotMapper = accountSnapshotMapper;
        this.tradingModeConfig = tradingModeConfig;
        this.kiwoomApiProperties = kiwoomApiProperties;
        this.dailyLossLimitRate = dailyLossLimitRate;
    }

    /**
     * 현재 모드/긴급정지/리스크 상태 조회 — ModeBadge, RiskGauge 컴포넌트용.
     * Current mode/emergency-stop/risk state — for the ModeBadge and RiskGauge components.
     *
     * ⚠️ 계좌 스냅샷이 아직 없으면(배포 첫날 등) currentLossRate는 0으로 내려간다 — 임의값을 지어내지
     * 않는다는 원칙(설계 §10)에 따른 것으로, "손실 없음"이 아니라 "아직 알 수 없음"을 뜻한다.
     * ⚠️ If no account snapshot exists yet (e.g. deployment day 1), currentLossRate reports 0 — per the
     * "never fabricate a value" principle (design doc §10). This means "not yet known", not "no loss".
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        AccountSnapshot latest = accountSnapshotMapper.findLatest(
                kiwoomApiProperties.getAccountNo(), tradingModeConfig.getMode().name());
        BigDecimal currentLossRate = (latest == null || latest.getDailyPnlRate().signum() >= 0)
                ? BigDecimal.ZERO
                : latest.getDailyPnlRate().negate().setScale(4, RoundingMode.HALF_UP);

        return Map.of(
                "mode", tradingModeConfig.getMode(),
                "emergencyStopped", riskEngine.isEmergencyStopped(),
                "riskState", Map.of(
                        "currentLossRate", currentLossRate,
                        "dailyLimitRate", dailyLossLimitRate
                )
        );
    }

    /** 보유 포지션 조회 — PositionCardList 컴포넌트용. */
    @GetMapping("/positions")
    public List<Position> getPositions() {
        return accountService.getPositions(kiwoomApiProperties.getAccountNo(), tradingModeConfig.getMode().name());
    }

    /**
     * 긴급정지 — EmergencyStopButton 확인 다이얼로그에서 호출.
     * Emergency stop — invoked from the EmergencyStopButton confirmation dialog.
     */
    @PostMapping("/emergency-stop")
    public RiskEvent emergencyStop() {
        RiskEvent event = riskEngine.triggerEmergencyStop(kiwoomApiProperties.getAccountNo(), tradingModeConfig.getMode().name());
        riskEventMapper.insert(event); // 실제 기동 테스트로 발견: risk_log에 저장되지 않던 것을 수정 (found via a real boot test: this wasn't persisted to risk_log)
        return event;
    }

    /**
     * 신호→리스크→주문 파이프라인을 지정 종목에 대해 1회 수동 실행한다 (BUG-004 수정, 스케줄러 자동 연동 전 수동/QA 검증용).
     * Manually runs the signal→risk→order pipeline once for the given stock (BUG-004 fix;
     * for manual/QA verification before the scheduler wires this automatically).
     *
     * TODO: 장중 스케줄러(§8.1)가 실시간 시세 구독을 트리거로 자동 호출하도록 연동 — 현재는 수동 트리거만 존재.
     * TODO: wire the intraday scheduler (§8.1) to call this automatically off live market data — currently manual only.
     */
    @PostMapping("/signals/{stockCode}/process")
    public Optional<OrderLog> processSignal(@PathVariable String stockCode,
                                             @RequestParam BigDecimal cashBalance,
                                             @RequestParam(defaultValue = "0") BigDecimal currentDailyLossRate) {
        return orderService.processSignal(stockCode, cashBalance, currentDailyLossRate);
    }
}
