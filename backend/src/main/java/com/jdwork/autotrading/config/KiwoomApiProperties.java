package com.jdwork.autotrading.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 키움증권 REST API 접속 정보. 앱키/시크릿은 환경변수로만 주입하며 코드/저장소에 절대 하드코딩하지 않는다.
 * Kiwoom REST API credentials. App key/secret must be injected via env vars only — never hardcoded.
 */
@Configuration
@ConfigurationProperties(prefix = "trading.kiwoom")
public class KiwoomApiProperties {

    private String baseUrl;
    private String appKey;
    private String appSecret;
    private String accountNo;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }
}
