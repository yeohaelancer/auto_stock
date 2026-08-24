package com.jdwork.autotrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 모의투자/실거래 모드 설정. 코드 배포가 아닌 설정값 변경만으로 전환한다 (설계 원칙 §3.3).
 * Paper/live trading mode config. Switched via config value only, never via code deploy (design doc §3.3).
 */
@Configuration
@ConfigurationProperties(prefix = "trading")
public class TradingModeConfig {

    /** MOCK(모의투자) 또는 LIVE(실거래). Mode: MOCK (paper) or LIVE (real). */
    private TradingMode mode = TradingMode.MOCK;

    public TradingMode getMode() {
        return mode;
    }

    public void setMode(TradingMode mode) {
        this.mode = mode;
    }

    public boolean isLive() {
        return mode == TradingMode.LIVE;
    }

    public enum TradingMode {
        MOCK, LIVE
    }
}
