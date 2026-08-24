package com.jdwork.autotrading.account.mapper;

import com.jdwork.autotrading.account.domain.AccountSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * account_snapshot 테이블 MyBatis 매퍼.
 * MyBatis mapper for the account_snapshot table.
 */
@Mapper
public interface AccountSnapshotMapper {

    /**
     * 가장 최근 스냅샷을 조회한다. 없으면 null.
     * Fetch the most recent snapshot, or null if none exists.
     *
     * 장중 스케줄러는 이 값을 현금 잔고/손실률 근사치로 사용한다 — 스냅샷이 없으면
     * 스캔 사이클 자체를 스킵해야 한다 (임의값 대체 금지, 설계 §10).
     * The intraday scheduler uses this as an approximation of cash balance/loss rate — when no
     * snapshot exists, the scan cycle must be skipped entirely (never substitute a fabricated value, design doc §10).
     */
    AccountSnapshot findLatest(@Param("accountId") String accountId, @Param("tradingMode") String tradingMode);

    /**
     * 스냅샷을 저장한다. 동일 (account_id, trading_mode, snapshot_date)가 이미 있으면 갱신한다(upsert) —
     * 스케줄러가 같은 날 재실행되어도 안전하다.
     * Saves a snapshot; upserts if one already exists for the same (account_id, trading_mode, snapshot_date) —
     * safe even if the scheduler re-runs on the same day.
     */
    void upsert(AccountSnapshot snapshot);
}
