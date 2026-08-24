<!--
  리스크 상태 게이지 — 일일 손실 한도 대비 현재 손실률 표시 (Designer 명세 §3).
  Risk status gauge — shows current loss rate vs the daily loss limit (Designer spec §3).
  색상만으로 정보를 전달하지 않도록 퍼센트 텍스트를 항상 병행 표기한다 (접근성).
  Always shows the percentage as text alongside color, for accessibility.
-->
<template>
  <div class="risk-gauge" :class="level">
    <span class="label">일일 손실 한도 대비</span>
    <span class="value">{{ percentText }}%</span>
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
const level = computed(() => {
  if (ratio.value >= 0.9) return 'critical'
  if (ratio.value >= 0.7) return 'warning'
  return 'safe'
})
</script>

<style scoped>
.risk-gauge.safe { color: var(--risk-safe, #4caf50); }
.risk-gauge.warning { color: var(--risk-warning, #ff9800); }
.risk-gauge.critical { color: var(--risk-critical, #d9534f); font-weight: 700; }
</style>
