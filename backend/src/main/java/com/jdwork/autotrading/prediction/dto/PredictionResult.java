package com.jdwork.autotrading.prediction.dto;

import java.math.BigDecimal;

/**
 * AI 예측 서비스(FastAPI) 응답 DTO.
 * Response DTO from the AI prediction service (FastAPI).
 */
public record PredictionResult(
        String stockCode,
        String direction,      // UP / DOWN / FLAT
        BigDecimal confidence, // 0.0 ~ 1.0
        BigDecimal expectedReturn, // nullable
        String modelVersion
) {
}
