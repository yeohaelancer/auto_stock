package com.jdwork.autotrading.api;

import com.jdwork.autotrading.backtest.BacktestResult;
import com.jdwork.autotrading.backtest.BacktestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 백테스트 성과 조회 REST 컨트롤러 (설계 §4.5).
 * REST controller exposing backtest performance results (design doc §4.5).
 */
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /**
     * order_log에 쌓인 체결 이력(현재는 대부분 MOCK)을 기반으로 누적수익률/MDD/승률/샤프비율을 계산한다.
     * Computes cumulative return / MDD / win rate / Sharpe ratio from order_log fills (mostly MOCK for now).
     *
     * 실거래 전환 승인 기준(설계 §4.5 "백테스트 결과가 일정 기준 미달 시 실거래 전환 보류")으로 사용할 것.
     * Use this as the go/no-go gate for live trading approval (design doc §4.5).
     */
    @GetMapping("/performance")
    public BacktestResult performance(@RequestParam(defaultValue = "MOCK") String tradingMode,
                                       @RequestParam(required = false) BigDecimal initialCapital) {
        return backtestService.runForMode(tradingMode, initialCapital);
    }
}
