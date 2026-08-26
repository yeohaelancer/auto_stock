<!--
  상단 네비게이션 바 — 로고뱃지 + 워드마크 + 모드 태그 + 긴급정지 버튼. design/Trading Dashboard.dc.html 1번 섹션 반영.
  Top nav bar — logo badge + wordmark + mode tag + emergency stop button. Matches design/Trading Dashboard.dc.html section 1.
-->
<template>
  <nav class="nav panel app-nav">
    <div style="display: flex; align-items: center; gap: var(--space-3);">
      <div class="icon-badge" style="width: 40px; height: 40px; background: var(--color-accent-2-100); color: var(--color-accent-2-800);">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"></path><path d="m19 9-5 5-4-4-3 3"></path></svg>
      </div>
      <span class="nav-brand" style="margin-right: 0;">{{ mode === 'LIVE' ? '실거래 대시보드' : '모의투자 대시보드' }}</span>
      <span class="tag tag-accent-2">자동매매 · {{ mode === 'LIVE' ? '실거래' : '모의' }}</span>
    </div>
    <EmergencyStopButton :disabled="emergencyStopped" @confirm="$emit('emergencyStop')" />
  </nav>
</template>

<script setup>
import EmergencyStopButton from './EmergencyStopButton.vue'

defineProps({
  mode: { type: String, default: 'MOCK' },
  emergencyStopped: { type: Boolean, default: false }
})
defineEmits(['emergencyStop'])
</script>
