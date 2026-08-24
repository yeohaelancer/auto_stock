<!--
  긴급정지 버튼 — 자동매매 즉시 중단 (Designer 명세 §3, §4 참고).
  Emergency stop button — halts auto-trading immediately (see Designer spec §3, §4).
  오조작 방지를 위해 클릭 시 반드시 확인 다이얼로그를 거친다.
  Requires a confirmation dialog before firing, to prevent accidental clicks.
-->
<template>
  <button
    class="emergency-stop-button"
    type="button"
    aria-label="자동매매 긴급 정지"
    :disabled="disabled"
    @click="dialogOpen = true"
  >
    🛑 긴급정지
  </button>

  <div v-if="dialogOpen" class="confirm-dialog" role="dialog" aria-modal="true">
    <p>정말 모든 자동매매를 중단하시겠습니까?</p>
    <button type="button" @click="dialogOpen = false">취소</button>
    <button type="button" class="danger" @click="confirm">중단하기</button>
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

<style scoped>
/* 색상 토큰은 기존 JD WORK 디자인 시스템 값을 상속받아 사용 (Designer 산출물 참고) */
/* Color tokens inherit from the existing JD WORK design system (see Designer output) */
.emergency-stop-button {
  font-weight: 700;
  background: var(--risk-critical, #d9534f);
  color: #fff;
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
}
.confirm-dialog .danger {
  background: var(--risk-critical, #d9534f);
  color: #fff;
}
</style>
