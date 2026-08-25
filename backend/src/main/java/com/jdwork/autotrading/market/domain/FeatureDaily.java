package com.jdwork.autotrading.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 모델 입력 피처 도메인 모델 (DB 테이블 feature_daily 매핑).
 * AI model input feature domain model (maps to feature_daily table).
 */
public class FeatureDaily {

    private String stockCode;
    private LocalDate baseDate;
    private BigDecimal ma5;
    private BigDecimal ma20;
    private BigDecimal rsi14;
    private BigDecimal macd;
    private BigDecimal bollingerUpper;
    private BigDecimal bollingerLower;
    private String featureVersion;

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    public LocalDate getBaseDate() { return baseDate; }
    public void setBaseDate(LocalDate baseDate) { this.baseDate = baseDate; }
    public BigDecimal getMa5() { return ma5; }
    public void setMa5(BigDecimal ma5) { this.ma5 = ma5; }
    public BigDecimal getMa20() { return ma20; }
    public void setMa20(BigDecimal ma20) { this.ma20 = ma20; }
    public BigDecimal getRsi14() { return rsi14; }
    public void setRsi14(BigDecimal rsi14) { this.rsi14 = rsi14; }
    public BigDecimal getMacd() { return macd; }
    public void setMacd(BigDecimal macd) { this.macd = macd; }
    public BigDecimal getBollingerUpper() { return bollingerUpper; }
    public void setBollingerUpper(BigDecimal bollingerUpper) { this.bollingerUpper = bollingerUpper; }
    public BigDecimal getBollingerLower() { return bollingerLower; }
    public void setBollingerLower(BigDecimal bollingerLower) { this.bollingerLower = bollingerLower; }
    public String getFeatureVersion() { return featureVersion; }
    public void setFeatureVersion(String featureVersion) { this.featureVersion = featureVersion; }
}
