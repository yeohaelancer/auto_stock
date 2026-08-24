package com.jdwork.autotrading.prediction;

import com.jdwork.autotrading.prediction.dto.PredictionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;

/**
 * AI 예측 서비스(FastAPI, POST /predict) 호출 클라이언트.
 * Client for the AI prediction service (FastAPI, POST /predict). Spring Boot와 분리된 별도 프로세스로 운영 (설계 §4.4).
 * Runs as a separate process from Spring Boot (design doc §4.4).
 */
@Component
public class PredictionClient {

    private final WebClient webClient;

    public PredictionClient(WebClient.Builder builder,
                             @Value("${trading.prediction.service-url}") String serviceUrl) {
        this.webClient = builder.baseUrl(serviceUrl).build();
    }

    /**
     * 지정 종목/기준일에 대한 예측을 요청한다.
     * Request a prediction for the given stock code and base date.
     *
     * 예측 서비스 응답 지연/실패 시 해당 종목 신호 생성을 스킵한다 — 절대 임의 값으로 대체하지 않음 (설계 §10 장애 대응 원칙).
     * On timeout/failure, callers must skip signal generation for that stock — never substitute a fabricated value (design doc §10).
     */
    public PredictionResult predict(String stockCode, LocalDate baseDate) {
        return webClient.post()
                .uri("/predict")
                .bodyValue(new PredictRequest(stockCode, baseDate.toString()))
                .retrieve()
                .bodyToMono(PredictionResult.class)
                .block(); // 호출부(SignalEngine)에서 타임아웃/예외를 캐치해 신호 생성을 스킵해야 함
    }

    private record PredictRequest(String stockCode, String baseDate) {
    }
}
