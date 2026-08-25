package com.jdwork.autotrading.market;

import com.jdwork.autotrading.market.domain.FeatureDaily;
import com.jdwork.autotrading.market.dto.PriceBar;
import com.jdwork.autotrading.market.mapper.FeatureDailyMapper;
import com.jdwork.autotrading.market.mapper.PriceHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * price_history의 실제 시세로 AI 모델 입력 피처(기술적 지표)를 계산해 feature_daily에 저장한다.
 * Computes AI model input features (technical indicators) from real price_history data and saves
 * them to feature_daily.
 *
 * 표준 공식을 사용한다 — SMA(단순이동평균), RSI(14, Wilder 방식 아닌 단순평균 방식), MACD(EMA12-EMA26),
 * 볼린저밴드(MA20 ± 2*표준편차). 지표별로 필요한 최소 데이터가 부족하면 해당 지표만 null로 남긴다
 * (feature_daily 컬럼은 모두 nullable — 임의 값으로 채우지 않음, 설계 §10).
 * Uses standard formulas — SMA, RSI(14, simple-average variant rather than Wilder smoothing),
 * MACD (EMA12-EMA26), Bollinger Bands (MA20 ± 2*stddev). If a given indicator lacks enough history,
 * it's simply left null (all feature_daily columns are nullable — never fabricate a value, design doc §10).
 *
 * ✅ 과거 전 구간 백필(computeAndSaveHistory): price_history에서 한 번에 최대 60일치를 받아오면서도
 * 정작 가장 최근 하루치 피처만 저장하던 것을 고쳐, MACD 계산이 가능한 구간(26일차 이후) 전체에 대해
 * feature_daily를 한 번에 채운다 — AI 모델이 실데이터 학습 임계치(train.py MIN_REAL_SAMPLES)에
 * 도달하는 시간을 "몇 주"에서 "며칠"로 단축하기 위한 개선.
 * ✅ Full-history backfill (computeAndSaveHistory): previously only the latest day's features were
 * saved even though up to 60 days of price_history were already fetched. Now backfills feature_daily
 * for the entire computable range (from day 26 onward) in one pass — shortens the time to reach
 * train.py's real-data threshold (MIN_REAL_SAMPLES) from "weeks" to "days".
 */
@Service
public class FeatureEngineeringService {

    private static final Logger log = LoggerFactory.getLogger(FeatureEngineeringService.class);

    /** 피처 산출 로직 버전 — 계산식이 바뀌면 반드시 올릴 것 (기존 데이터와 구분하기 위함). */
    private static final String FEATURE_VERSION = "v1-standard-indicators";

    private static final int WARMUP_BARS = 60; // EMA26 등의 수렴 여유를 둔 조회 개수
    private static final int MIN_BARS_FOR_MACD = 26; // 가장 까다로운 지표(MACD)의 최소 요구 구간
    private static final int SCALE = 4;

    private final PriceHistoryMapper priceHistoryMapper;
    private final FeatureDailyMapper featureDailyMapper;

    public FeatureEngineeringService(PriceHistoryMapper priceHistoryMapper, FeatureDailyMapper featureDailyMapper) {
        this.priceHistoryMapper = priceHistoryMapper;
        this.featureDailyMapper = featureDailyMapper;
    }

    /**
     * 지정 종목의 price_history 중 계산 가능한 전 구간(26일차 이후)의 피처를 한 번에 계산·저장한다.
     * Computes and saves features for the whole computable range (day 26 onward) of the stock's
     * price_history in one pass.
     */
    public void computeAndSaveHistory(String stockCode) {
        List<PriceBar> recentDesc = priceHistoryMapper.findRecent(stockCode, "DAILY", WARMUP_BARS);
        if (recentDesc.size() < MIN_BARS_FOR_MACD) {
            return; // MACD조차 계산 못 하는 데이터량이면 스킵 (skip if not even enough for MACD)
        }
        List<PriceBar> chronological = new ArrayList<>(recentDesc);
        Collections.reverse(chronological); // 오래된 것 → 최신 순으로 정렬

        List<Double> allCloses = chronological.stream().map(bar -> bar.close().doubleValue()).toList();

        int saved = 0;
        for (int i = MIN_BARS_FOR_MACD - 1; i < chronological.size(); i++) {
            List<Double> closesUpToDate = allCloses.subList(0, i + 1);
            LocalDate baseDate = chronological.get(i).tradeDateTime().toLocalDate();
            featureDailyMapper.upsert(buildFeature(stockCode, baseDate, closesUpToDate));
            saved++;
        }
        log.debug("{} 종목 피처 {}건 백필 완료 ({} feature rows backfilled for {})", stockCode, saved, saved, stockCode);
    }

    private FeatureDaily buildFeature(String stockCode, LocalDate baseDate, List<Double> closes) {
        FeatureDaily feature = new FeatureDaily();
        feature.setStockCode(stockCode);
        feature.setBaseDate(baseDate);
        feature.setMa5(sma(closes, 5));
        feature.setMa20(sma(closes, 20));
        feature.setRsi14(rsi(closes, 14));
        feature.setMacd(macd(closes));
        BigDecimal[] bollinger = bollinger(closes, 20, 2.0);
        feature.setBollingerUpper(bollinger[0]);
        feature.setBollingerLower(bollinger[1]);
        feature.setFeatureVersion(FEATURE_VERSION);
        return feature;
    }

    private BigDecimal sma(List<Double> closes, int period) {
        if (closes.size() < period) {
            return null;
        }
        List<Double> window = closes.subList(closes.size() - period, closes.size());
        double avg = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return round(avg);
    }

    private BigDecimal rsi(List<Double> closes, int period) {
        if (closes.size() < period + 1) {
            return null;
        }
        List<Double> window = closes.subList(closes.size() - period - 1, closes.size());
        double gainSum = 0;
        double lossSum = 0;
        for (int i = 1; i < window.size(); i++) {
            double diff = window.get(i) - window.get(i - 1);
            if (diff > 0) {
                gainSum += diff;
            } else {
                lossSum += -diff;
            }
        }
        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;
        if (avgLoss == 0) {
            return round(100); // 하락이 전혀 없었던 경우 (no losses in the window)
        }
        double rs = avgGain / avgLoss;
        double rsiValue = 100 - (100 / (1 + rs));
        return round(rsiValue);
    }

    private BigDecimal macd(List<Double> closes) {
        if (closes.size() < 26) {
            return null; // EMA26을 계산할 최소 데이터도 부족 (not enough data even for EMA26)
        }
        double ema12 = ema(closes, 12);
        double ema26 = ema(closes, 26);
        return round(ema12 - ema26);
    }

    /** 단순 이동평균을 시작값으로 삼아 지수이동평균을 순차 계산한다 (표준적인 근사 방식). */
    private double ema(List<Double> closes, int period) {
        double smoothing = 2.0 / (period + 1);
        List<Double> seed = closes.subList(0, period);
        double ema = seed.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        for (int i = period; i < closes.size(); i++) {
            ema = (closes.get(i) - ema) * smoothing + ema;
        }
        return ema;
    }

    private BigDecimal[] bollinger(List<Double> closes, int period, double numStdDev) {
        if (closes.size() < period) {
            return new BigDecimal[]{null, null};
        }
        List<Double> window = closes.subList(closes.size() - period, closes.size());
        double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = window.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);
        return new BigDecimal[]{round(mean + numStdDev * stdDev), round(mean - numStdDev * stdDev)};
    }

    private BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
