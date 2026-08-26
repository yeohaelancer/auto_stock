<!--
  "지금 상태" 3카드 행 — 보유 포지션 / 일일 손실 한도 대비 / 평가금액(오늘). design/Trading Dashboard.dc.html 2번 섹션 반영.
  "Current status" 3-card row — position / daily loss-limit usage / today's valuation. Matches design/Trading Dashboard.dc.html section 2.
-->
<template>
  <section>
    <p class="eyebrow">지금 상태</p>
    <div class="stat-grid">
      <div class="card elev-sm panel stat-card">
        <div class="icon-badge" style="background: var(--color-neutral-100); color: var(--color-neutral-700);">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2 2 7l10 5 10-5-10-5Z"></path><path d="M2 17l10 5 10-5"></path><path d="M2 12l10 5 10-5"></path></svg>
        </div>
        <div>
          <div class="metric-label">보유 포지션</div>
          <div class="metric-num" style="font-size: 20px;">
            {{ positions.length === 0 ? '보유 종목 없음' : `${positions.length}종목` }}
          </div>
        </div>
      </div>

      <RiskGauge :current-loss-rate="riskState.currentLossRate" :daily-limit-rate="riskState.dailyLimitRate" />

      <div class="card elev-sm panel stat-card">
        <div class="icon-badge" style="background: var(--color-neutral-100); color: var(--color-neutral-700);">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"></path><path d="M7 15v-5"></path><path d="M12 15V7"></path><path d="M17 15v-3"></path></svg>
        </div>
        <div>
          <div class="metric-label">평가금액 (오늘)</div>
          <div class="metric-num">{{ todayValuation }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import RiskGauge from './RiskGauge.vue'

const props = defineProps({
  positions: { type: Array, default: () => [] },
  riskState: { type: Object, required: true },
  snapshots: { type: Array, default: () => [] } // 최신순 정렬된 계좌 스냅샷 목록 (snapshots ordered newest-first)
})

// 스냅샷이 아직 없으면 임의값을 지어내지 않고 '-' 표시 (설계 §10 "임의값 대체 금지" 원칙과 일관)
// No snapshot yet -> show '-' instead of fabricating a number (consistent with the backend's "never fabricate a value" principle, design doc §10)
const todayValuation = computed(() => {
  const latest = props.snapshots[0]
  return latest ? Number(latest.totalValue).toLocaleString('ko-KR') : '-'
})
</script>
