package com.jdwork.autotrading.strategy;

import com.jdwork.autotrading.prediction.PredictionClient;
import com.jdwork.autotrading.prediction.dto.PredictionResult;
import com.jdwork.autotrading.strategy.domain.StrategySignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * AI 예측 결과를 매매 신호(BUY/SELL/HOLD)로 변환하는 엔진 (설계 §5.1).
 * Converts AI prediction results into trading signals (BUY/SELL/HOLD) (design doc §5.1).
 *
 * 리스크 검증은 이 클래스가 아닌 별도 RiskEngine에서 수행한다 — 전략 로직 버그가 리스크 방어선까지 뚫지 못하도록 완전히 독립시킨다 (설계 §6).
 * Risk validation is handled by a separate RiskEngine, not here — kept fully independent so a strategy bug
 * can never bypass the risk safety net (design doc §6).
 */
@Service
public class SignalEngine {

    private static final Logger log = LoggerFactory.getLogger(SignalEngine.class);

    /** 신뢰도 임계값 이하 예측은 무시한다 (설계 §5.1). Predictions below this confidence are ignored. */
    private static final BigDecimal CONFIDENCE_THRESHOLD = new BigDecimal("0.6");

    private final PredictionClient predictionClient;

    public SignalEngine(PredictionClient predictionClient) {
        this.predictionClient = predictionClient;
    }

    /**
     * 지정 종목에 대한 신호를 생성한다. 예측 서비스 실패 시 empty를 반환하고 매매하지 않는다.
     * Generate a signal for the given stock. Returns empty (no trade) if the prediction service fails.
     */
    public Optional<StrategySignal> generateSignal(String stockCode) {
        PredictionResult prediction;
        try {
            prediction = predictionClient.predict(stockCode, LocalDate.now());
        } catch (Exception e) {
            // 예측 서비스 응답 지연/실패 → 신호 생성 스킵, 임의 값으로 대체하지 않음 (설계 §10)
            // Prediction service failed/timed out → skip signal generation, never substitute a fabricated value (design doc §10)
            log.warn("AI 예측 실패로 {} 종목 신호 생성을 스킵합니다. Prediction failed, skipping signal for {}", stockCode, stockCode, e);
            return Optional.empty();
        }

        if (prediction.confidence().compareTo(CONFIDENCE_THRESHOLD) < 0) {
            return Optional.empty(); // 신뢰도 미달 → 신호 없음 (below confidence threshold → no signal)
        }

        StrategySignal signal = new StrategySignal();
        signal.setSignalId(UUID.randomUUID());
        signal.setStockCode(stockCode);
        signal.setGeneratedAt(OffsetDateTime.now());
        signal.setStatus(StrategySignal.SignalStatus.PENDING);
        signal.setSignalType(toSignalType(prediction.direction()));
        return Optional.of(signal);
    }

    private StrategySignal.SignalType toSignalType(String direction) {
        return switch (direction) {
            case "UP" -> StrategySignal.SignalType.BUY;
            case "DOWN" -> StrategySignal.SignalType.SELL;
            default -> StrategySignal.SignalType.HOLD;
        };
    }
}
