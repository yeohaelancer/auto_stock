<!--
  단독 실행용 루트 컴포넌트 — design/Trading Dashboard.dc.html의 전체 페이지 구조(nav → 지금 상태 →
  데이터 파이프라인 → 계좌 스냅샷 추이 → 주문/리스크 탭)를 그대로 반영한다.
  JD WORK 등 외부 호스트 대시보드에 의존하지 않는다.
  Standalone root component — recreates the full page structure of design/Trading Dashboard.dc.html
  (nav → current status → data pipeline → account snapshot trend → orders/risk tabs) as-is.
  Not dependent on an external host dashboard such as JD WORK.
-->
<template>
  <div style="min-height: 100vh; font-family: var(--font-body); padding-bottom: var(--space-8);">
    <AppNav
      :mode="status.mode"
      :emergency-stopped="status.emergencyStopped"
      @emergency-stop="emergencyStop"
    />

    <main class="app-main">
      <p v-if="connectionError" class="connection-warning">⚠️ 백엔드 연결 끊김 — 데이터가 최신이 아닐 수 있음</p>

      <StatusStatsRow :positions="positions" :risk-state="status.riskState" :snapshots="snapshots" />
      <CollectionStatusPanel :status="collectionStatus" />
      <AccountSnapshotPanel :snapshots="snapshots" />
      <ActivityTabs :orders="orders" :risk-events="riskEvents" />
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import AppNav from './components/AppNav.vue'
import StatusStatsRow from './components/StatusStatsRow.vue'
import CollectionStatusPanel from './components/CollectionStatusPanel.vue'
import AccountSnapshotPanel from './components/AccountSnapshotPanel.vue'
import ActivityTabs from './components/ActivityTabs.vue'
import { useTradingData } from './composables/useTradingData.js'

const {
  status, positions, snapshots, orders, riskEvents, collectionStatus, connectionError,
  loadAll, emergencyStop
} = useTradingData()

onMounted(loadAll)
</script>
