<!--
  긴급정지 버튼 — 자동매매 즉시 중단. design/Trading Dashboard.dc.html의 nav 버튼 + dialog 스타일을 그대로 반영.
  Emergency stop button — halts auto-trading immediately. Matches the nav button + dialog styling from design/Trading Dashboard.dc.html.
  오조작 방지를 위해 클릭 시 반드시 확인 다이얼로그를 거친다.
  Requires a confirmation dialog before firing, to prevent accidental clicks.
-->
<template>
  <button
    type="button"
    class="btn btn-primary"
    style="background: var(--color-accent-900); border-color: var(--color-accent-900); font-weight: 700;"
    aria-label="자동매매 긴급 정지"
    :disabled="disabled"
    @click="dialogOpen = true"
  >
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.75" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"></path><path d="M12 9v4"></path><path d="M12 17h.01"></path></svg>
    긴급정지
  </button>

  <div v-if="dialogOpen" class="dialog-backdrop" @click.self="dialogOpen = false">
    <div class="dialog" role="dialog" aria-modal="true">
      <div class="dialog-title">긴급정지</div>
      <p class="dialog-body">정말 모든 자동매매를 중단하시겠습니까?</p>
      <div class="dialog-actions">
        <button type="button" class="btn btn-secondary" @click="dialogOpen = false">취소</button>
        <button type="button" class="btn btn-primary" style="background: var(--risk-critical);" @click="confirm">중단하기</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['confirm'])
const dialogOpen = ref(false)

function confirm() {
  dialogOpen.value = false
  emit('confirm')
}
</script>
