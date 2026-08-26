<!--
  "시세/피처 수집 현황" 카드 — 매매 유니버스 + 시세 데이터 + AI 피처 3열 + 안내 배너. design/Trading Dashboard.dc.html 3번 섹션 반영.
  "Price/feature collection status" card — universe + price data + AI feature 3-column row + info banner. Matches design/Trading Dashboard.dc.html section 3.
-->
<template>
  <section class="card elev-sm panel" style="padding: var(--space-6); gap: var(--space-5);">
    <div>
      <p class="eyebrow" style="margin-bottom: var(--space-1);">시세/피처 수집 현황</p>
      <div class="card-title" style="font-size: 22px;">데이터 파이프라인</div>
    </div>

    <div class="pipeline-grid">
      <div style="display: flex; align-items: flex-start; gap: var(--space-3);">
        <div class="icon-badge" style="width: 44px; height: 44px; background: var(--color-neutral-100); color: var(--color-neutral-700);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><path d="M12 6v6l4 2"></path></svg>
        </div>
        <div>
          <div class="metric-label">매매 유니버스</div>
          <div class="metric-num">{{ status.universeActiveCount }} / {{ status.universeTotalCount }}</div>
          <div class="metric-sub">활성 / 전체 종목</div>
        </div>
      </div>

      <div style="display: flex; align-items: flex-start; gap: var(--space-3);">
        <div class="icon-badge" style="width: 44px; height: 44px; background: var(--color-neutral-100); color: var(--color-neutral-700);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"></path><path d="m19 9-5 5-4-4-3 3"></path></svg>
        </div>
        <div>
          <div class="metric-label">시세 데이터</div>
          <div class="metric-num">{{ status.priceHistoryCount.toLocaleString('ko-KR') }}건</div>
          <div class="metric-sub">{{ status.priceHistoryStockCount }}종목 · 마지막 수집 {{ formatTime(status.priceHistoryLastCollectedAt) }}</div>
        </div>
      </div>

      <div style="display: flex; align-items: flex-start; gap: var(--space-3);">
        <div class="icon-badge" style="width: 44px; height: 44px; background: var(--color-neutral-100); color: var(--color-neutral-700);">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a10 10 0 1 0 10 10"></path><path d="M12 6v6l4 2"></path><path d="M18 2l2 2-2 2"></path></svg>
        </div>
        <div>
          <div class="metric-label">AI 피처</div>
          <div class="metric-num">{{ status.featureDailyCount.toLocaleString('ko-KR') }}건</div>
          <div class="metric-sub">{{ status.featureDailyStockCount }}종목 · 마지막 산출일 {{ status.featureDailyLastBaseDate ?? '-' }}</div>
        </div>
      </div>
    </div>

    <div v-if="status.priceHistoryCount === 0" style="background: var(--color-accent-2-100); border-radius: var(--radius-md); padding: var(--space-4); font-size: 13px; color: var(--color-accent-2-800); line-height: 1.6; display: flex; gap: var(--space-3); align-items: flex-start;">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round" style="flex: none; margin-top: 1px;"><circle cx="12" cy="12" r="10"></circle><path d="M12 16v-4"></path><path d="M12 8h.01"></path></svg>
      <span>아직 수집된 시세가 없습니다 — 장전 유니버스 선정(08:00)과 시세/피처 수집 배치(16:00, 평일)가 한 번은 지나야 채워집니다.</span>
    </div>
  </section>
</template>

<script setup>
defineProps({
  status: { type: Object, required: true }
})

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>
