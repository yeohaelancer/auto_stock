package com.jdwork.autotrading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주식 자동매매 백엔드 애플리케이션 진입점
 * Entry point for the stock auto-trading backend application.
 */
@SpringBootApplication
@EnableScheduling // 장전/장중/장마감 배치 스케줄러 활성화 (enable pre/intra/post-market schedulers)
public class AutoTradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoTradingApplication.class, args);
    }
}
