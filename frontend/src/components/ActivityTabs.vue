<!--
  "주문/체결 이력" · "리스크 이벤트 로그" 탭 카드. design/Trading Dashboard.dc.html 5번 섹션 반영.
  탭 전환은 로컬 UI 상태일 뿐 별도 라우팅/재조회 없음 (README "State Management": activeTab은 local UI state).
  "Orders" / "Risk event log" tabbed card. Matches design/Trading Dashboard.dc.html section 5.
  Tab switching is local UI state only, no routing/re-fetch (per the design README's State Management note).
-->
<template>
  <section class="card elev-sm panel" style="padding: var(--space-6); gap: var(--space-4);">
    <div class="tab-bar" role="tablist">
      <button type="button" class="tab-btn" :data-active="tab === 'orders'" @click="tab = 'orders'">주문/체결 이력</button>
      <button type="button" class="tab-btn" :data-active="tab === 'risk'" @click="tab = 'risk'">리스크 이벤트 로그</button>
    </div>

    <template v-if="tab === 'orders'">
      <div v-if="orders.length === 0" class="empty-state">
        <div class="icon-badge" style="width: 56px; height: 56px; background: var(--color-neutral-100); color: var(--color-neutral-600);">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M22 12h-6l-2 3h-4l-2-3H2"></path><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11Z"></path></svg>
        </div>
        <span>아직 주문 이력이 없습니다</span>
      </div>
      <div v-else style="overflow-x: auto;">
        <table class="table">
          <thead>
            <tr>
              <th>시각</th><th>종목</th><th>구분</th><th>수량</th><th>주문가</th><th>체결가</th><th>상태</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in orders" :key="o.orderId">
              <td>{{ formatTime(o.createdAt) }}</td>
              <td>{{ o.stockCode }}</td>
              <td>{{ o.orderType === 'BUY' ? '매수' : '매도' }}</td>
              <td>{{ o.quantity }}</td>
              <td>{{ formatPrice(o.orderPrice) }}</td>
              <td>{{ formatPrice(o.executedPrice) }}</td>
              <td>{{ statusLabel(o.executionStatus) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template v-else>
      <div v-if="riskEvents.length === 0" class="empty-state">
        <div class="icon-badge" style="width: 56px; height: 56px; background: var(--color-accent-2-100); color: var(--color-accent-2-800);">
          <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1Z"></path><path d="m9 12 2 2 4-4"></path></svg>
        </div>
        <span>아직 리스크 이벤트가 없습니다 — 모든 안전 장치가 정상 대기 중입니다</span>
      </div>
      <div v-else style="overflow-x: auto;">
        <table class="table">
          <thead>
            <tr><th>시각</th><th>유형</th><th>조치</th></tr>
          </thead>
          <tbody>
            <tr v-for="e in riskEvents" :key="e.riskLogId">
              <td>{{ formatTime(e.occurredAt) }}</td>
              <td>{{ typeLabel(e.eventType) }}</td>
              <td>{{ e.actionTaken }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  orders: { type: Array, default: () => [] },
  riskEvents: { type: Array, default: () => [] }
})

const tab = ref('orders')

const STATUS_LABELS = { PENDING: '미체결', FILLED: '체결', PARTIAL: '부분체결', CANCELLED: '취소', REJECTED: '거부' }
const TYPE_LABELS = {
  DAILY_LOSS_LIMIT: '일일 손실한도', POSITION_LIMIT: '포지션 한도',
  ANOMALY_DETECTED: '이상매매 감지', MANUAL_KILL_SWITCH: '긴급정지'
}

function statusLabel(status) { return STATUS_LABELS[status] ?? status }
function typeLabel(type) { return TYPE_LABELS[type] ?? type }
function formatPrice(value) { return value == null ? '-' : Number(value).toLocaleString('ko-KR') }
function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>
