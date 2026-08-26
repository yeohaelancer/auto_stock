<!--
  리스크 상태 게이지 — "지금 상태" 카드 2번(일일 손실 한도 대비). design/Trading Dashboard.dc.html 반영.
  Risk status gauge — the 2nd "current status" stat card (daily loss-limit usage). Matches design/Trading Dashboard.dc.html.
  색상만으로 정보를 전달하지 않도록 퍼센트 텍스트를 항상 병행 표기한다 (접근성).
  Always shows the percentage as text alongside color, for accessibility.
-->
<template>
  <div class="card elev-sm panel stat-card">
    <div class="icon-badge" style="background: var(--color-accent-2-100); color: var(--color-accent-2-800);">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"></path></svg>
    </div>
    <div style="flex: 1;">
      <div class="metric-label">일일 손실 한도 대비</div>
      <div class="metric-num">{{ percentText }}%</div>
      <div style="height: 6px; margin-top: var(--space-2); border-radius: 999px; background: var(--color-accent-2-100); overflow: hidden;">
        <div :style="{ height: '100%', width: percentText + '%', background: barColor, borderRadius: '999px' }"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentLossRate: { type: Number, required: true }, // 0 이상 양수로 전달 (당일 손실률 절대값)
  dailyLimitRate: { type: Number, required: true }
})

const ratio = computed(() =>
  props.dailyLimitRate > 0 ? Math.min(props.currentLossRate / props.dailyLimitRate, 1) : 0
)
const percentText = computed(() => Math.round(ratio.value * 100))
const barColor = computed(() => {
  if (ratio.value >= 0.9) return 'var(--risk-critical)'
  if (ratio.value >= 0.7) return 'var(--risk-warning)'
  return 'var(--color-accent-2-600)'
})
</script>
