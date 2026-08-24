<!--
  자동매매 위젯 — JD WORK 대시보드 카드 그리드에 삽입되는 최상위 컴포넌트.
  Auto-trading widget — top-level component inserted into the JD WORK dashboard card grid.
  Designer 명세: docs/agents/designer/screens/auto-trading-widget.md
-->
<template>
  <section class="auto-trading-widget" :class="{ 'emergency-stopped': status.emergencyStopped }">
    <header class="header-bar">
      <span class="mode-badge" :class="status.mode?.toLowerCase()">
        {{ status.mode === 'LIVE' ? '실거래' : '모의투자' }}
      </span>
      <EmergencyStopButton :disabled="status.emergencyStopped" @confirm="onEmergencyStop" />
    </header>

    <p v-if="connectionError" class="connection-warning">
      ⚠️ 시세 연결 끊김 — 신규 주문 중단됨
    </p>

    <section class="positions">
      <h3>포지션 현황</h3>
      <p v-if="positions.length === 0" class="empty">보유 종목 없음</p>
      <ul v-else>
        <li v-for="p in positions" :key="p.stockCode">
          {{ p.stockName }} ({{ p.stockCode }})
        </li>
      </ul>
    </section>

    <RiskGauge :current-loss-rate="riskState.currentLossRate" :daily-limit-rate="riskState.dailyLimitRate" />
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import EmergencyStopButton from './EmergencyStopButton.vue'
import RiskGauge from './RiskGauge.vue'

// 실제 배포 시 JD WORK 공통 API 클라이언트로 교체 (fetch는 배선 검증용)
// Replace with the shared JD WORK API client in production (fetch here is for wiring verification only)
const API_BASE = '/api/trading'

const status = reactive({ mode: 'MOCK', emergencyStopped: false })
const positions = ref([])
const riskState = reactive({ currentLossRate: 0, dailyLimitRate: 0.03 })
const connectionError = ref(false)

async function loadStatus() {
  try {
    const res = await fetch(`${API_BASE}/status`)
    Object.assign(status, await res.json())
    connectionError.value = false
  } catch {
    connectionError.value = true
  }
}

async function loadPositions() {
  try {
    const res = await fetch(`${API_BASE}/positions`)
    positions.value = await res.json()
  } catch {
    connectionError.value = true
  }
}

async function onEmergencyStop() {
  await fetch(`${API_BASE}/emergency-stop`, { method: 'POST' })
  await loadStatus()
}

onMounted(() => {
  loadStatus()
  loadPositions()
})
</script>

<style scoped>
.auto-trading-widget.emergency-stopped {
  border: 2px solid var(--risk-critical, #d9534f);
}
.mode-badge.live {
  background: var(--mode-live-bg, #fddede);
}
.mode-badge.mock {
  background: var(--mode-mock-bg, #dbe9ff);
}
.connection-warning {
  color: var(--risk-warning, #ff9800);
}
</style>
