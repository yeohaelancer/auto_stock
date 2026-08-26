// 대시보드 전체가 공유하는 데이터 계층 — 각 패널이 따로 fetch하지 않고 이 컴포저블 하나로 상태를 모은다.
// Shared data layer for the whole dashboard — panels don't fetch on their own; this composable centralizes state.
import { reactive, ref } from 'vue'

const API_TRADING = '/api/trading'
const API_MONITORING = '/api/monitoring'

export function useTradingData() {
  const status = reactive({
    mode: 'MOCK',
    emergencyStopped: false,
    riskState: { currentLossRate: 0, dailyLimitRate: 0.03 }
  })
  const positions = ref([])
  const snapshots = ref([])
  const orders = ref([])
  const riskEvents = ref([])
  const collectionStatus = ref({
    universeActiveCount: 0, universeTotalCount: 0,
    priceHistoryCount: 0, priceHistoryStockCount: 0, priceHistoryLastCollectedAt: null,
    featureDailyCount: 0, featureDailyStockCount: 0, featureDailyLastBaseDate: null
  })
  const connectionError = ref(false)

  async function loadStatus() {
    try {
      const res = await fetch(`${API_TRADING}/status`)
      if (!res.ok) throw new Error('request failed')
      const data = await res.json()
      Object.assign(status, data)
      if (data.riskState) Object.assign(status.riskState, data.riskState)
      connectionError.value = false
    } catch {
      connectionError.value = true
    }
  }

  async function loadPositions() {
    try {
      const res = await fetch(`${API_TRADING}/positions`)
      if (!res.ok) throw new Error('request failed')
      positions.value = await res.json()
    } catch {
      connectionError.value = true
    }
  }

  async function loadSnapshots() {
    try {
      const res = await fetch(`${API_MONITORING}/account-snapshots?tradingMode=${status.mode}&limit=30`)
      if (!res.ok) throw new Error('request failed')
      snapshots.value = await res.json()
    } catch {
      connectionError.value = true
    }
  }

  async function loadOrders() {
    try {
      const res = await fetch(`${API_MONITORING}/orders?tradingMode=${status.mode}&limit=50`)
      if (!res.ok) throw new Error('request failed')
      orders.value = await res.json()
    } catch {
      connectionError.value = true
    }
  }

  async function loadRiskEvents() {
    try {
      const res = await fetch(`${API_MONITORING}/risk-events?tradingMode=${status.mode}&limit=50`)
      if (!res.ok) throw new Error('request failed')
      riskEvents.value = await res.json()
    } catch {
      connectionError.value = true
    }
  }

  async function loadCollectionStatus() {
    try {
      const res = await fetch(`${API_MONITORING}/collection-status`)
      if (!res.ok) throw new Error('request failed')
      collectionStatus.value = await res.json()
    } catch {
      connectionError.value = true
    }
  }

  /** 모드를 먼저 확정한 뒤(status) 나머지를 병렬로 불러온다 — mode 파라미터가 필요한 조회들이 정확한 모드로 나가도록. */
  /** Resolves the mode first (status), then loads the rest in parallel — so mode-scoped queries use the right mode. */
  async function loadAll() {
    await loadStatus()
    await Promise.all([loadPositions(), loadSnapshots(), loadOrders(), loadRiskEvents(), loadCollectionStatus()])
  }

  async function emergencyStop() {
    await fetch(`${API_TRADING}/emergency-stop`, { method: 'POST' })
    await loadStatus()
    await loadRiskEvents()
  }

  return {
    status, positions, snapshots, orders, riskEvents, collectionStatus, connectionError,
    loadAll, loadStatus, loadPositions, loadSnapshots, loadOrders, loadRiskEvents, loadCollectionStatus,
    emergencyStop
  }
}
