package com.jdwork.autotrading.account;

import com.jdwork.autotrading.account.domain.Position;
import com.jdwork.autotrading.account.mapper.PositionMapper;
import com.jdwork.autotrading.risk.AccountRiskContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 계좌/포지션 조회 서비스.
 * Account/position lookup service.
 *
 * BUG-001 수정: PositionMapper 연동 완료 — 더 이상 빈 목록을 반환하지 않는다.
 * BUG-001 fix: wired up PositionMapper — no longer returns an empty list unconditionally.
 */
@Service
public class AccountService {

    private final PositionMapper positionMapper;

    public AccountService(PositionMapper positionMapper) {
        this.positionMapper = positionMapper;
    }

    public List<Position> getPositions(String accountId, String tradingMode) {
        return positionMapper.findByAccountAndMode(accountId, tradingMode);
    }

    /**
     * 리스크 엔진의 포지션/현금 비율 검증(BUG-002)에 필요한 계좌 컨텍스트를 구성한다.
     * Builds the account context needed by the risk engine's position/cash ratio checks (BUG-002).
     *
     * NOTE: 평가금액은 평균단가 기준 근사치(quantity * avgPrice)이며, 실시간 현재가 반영은
     * KiwoomMarketClient 실시간 시세 연동 후 개선 대상이다 (평균단가는 평가금액을 과소/과대평가할 수 있음).
     * NOTE: Valuation uses avgPrice * quantity as an approximation; incorporating live market prices
     * from KiwoomMarketClient is a follow-up improvement (avgPrice can under/overstate real valuation).
     *
     * @param cashBalance 현재 현금 잔고 — 실시간 잔고 조회 API 연동 전까지 호출부에서 전달 (TODO)
     *                    Current cash balance — passed by the caller until a live balance API is wired (TODO)
     */
    public AccountRiskContext getRiskContext(String accountId, String tradingMode, BigDecimal cashBalance, String targetStockCode) {
        List<Position> positions = getPositions(accountId, tradingMode);
        BigDecimal positionsValue = sumPositionValue(positions);

        BigDecimal targetPositionValue = positions.stream()
                .filter(p -> p.getStockCode().equals(targetStockCode))
                .findFirst()
                .map(p -> p.getAvgPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .orElse(BigDecimal.ZERO);

        BigDecimal totalAccountValue = cashBalance.add(positionsValue);

        return new AccountRiskContext(totalAccountValue, cashBalance, targetPositionValue);
    }

    /**
     * 보유 포지션 전체 평가액(평균단가 기준 근사치)을 합산한다 — 계좌 스냅샷 배치(BUG-006)에서도 재사용.
     * Sums the valuation of all held positions (avgPrice-based approximation) — reused by the
     * account snapshot batch job (BUG-006).
     */
    public BigDecimal getPositionsValue(String accountId, String tradingMode) {
        return sumPositionValue(getPositions(accountId, tradingMode));
    }

    private BigDecimal sumPositionValue(List<Position> positions) {
        return positions.stream()
                .map(p -> p.getAvgPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
