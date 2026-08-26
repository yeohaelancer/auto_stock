<!--
  "계좌 스냅샷 추이" 테이블 카드. design/Trading Dashboard.dc.html 4번 섹션 반영.
  "Account snapshot trend" table card. Matches design/Trading Dashboard.dc.html section 4.
-->
<template>
  <section class="card elev-sm panel" style="padding: var(--space-6); gap: var(--space-4);">
    <div>
      <p class="eyebrow" style="margin-bottom: var(--space-1);">계좌 스냅샷 추이</p>
      <div class="card-title" style="font-size: 22px;">일별 자산 현황</div>
    </div>

    <p v-if="snapshots.length === 0" class="metric-sub">
      아직 계좌 스냅샷 없음 — postMarketJob(15:40 KST, 평일) 실행 후 생성됩니다.
    </p>
    <table v-else class="table">
      <thead>
        <tr>
          <th>일자</th>
          <th>평가금액</th>
          <th>현금</th>
          <th>당일손익</th>
          <th>손익률</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in snapshots" :key="s.snapshotDate">
          <td>{{ s.snapshotDate }}</td>
          <td>{{ formatPrice(s.totalValue) }}</td>
          <td>{{ formatPrice(s.cashBalance) }}</td>
          <td>{{ formatPrice(s.dailyPnl) }}</td>
          <td>{{ formatPercent(s.dailyPnlRate) }}%</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup>
defineProps({
  snapshots: { type: Array, default: () => [] }
})

function formatPrice(value) {
  return value == null ? '-' : Number(value).toLocaleString('ko-KR')
}

function formatPercent(value) {
  return value == null ? '-' : (Number(value) * 100).toFixed(2)
}
</script>
