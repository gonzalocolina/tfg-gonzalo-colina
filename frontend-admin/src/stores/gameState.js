import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getGameState as fetchState } from '@/api/gameState'

export const useGameStateStore = defineStore('gameState', () => {
  const eventId = ref(null)
  const eventStatus = ref('')
  const eventResult = ref(null)
  const villainName = ref('')
  const villainType = ref(null)
  const villainHpCurrent = ref(0)
  const villainHpMax = ref(0)
  const schemeThreatCurrent = ref(0)
  const schemeThreatTarget = ref(0)
  const timerRemainingSeconds = ref(0)
  const timerStatus = ref('NOT_CONFIGURED')
  const totalReportedVillainDamage = ref(0)
  const startedAt = ref(null)
  const finishedAt = ref(null)
  const updatedAt = ref(null)
  const finalSummary = ref(null)

  function applySnapshot(s) {
    if (!s) return
    eventId.value = s.eventId
    eventStatus.value = s.eventStatus
    eventResult.value = s.eventResult
    villainName.value = s.villainName || ''
    villainType.value = s.villainType || null
    villainHpCurrent.value = s.villainHpCurrent
    villainHpMax.value = s.villainHpMax
    schemeThreatCurrent.value = s.schemeThreatCurrent
    schemeThreatTarget.value = s.schemeThreatTarget
    timerRemainingSeconds.value = s.timerRemainingSeconds
    timerStatus.value = s.timerStatus
    totalReportedVillainDamage.value = s.totalReportedVillainDamage
    startedAt.value = s.startedAt
    finishedAt.value = s.finishedAt
    updatedAt.value = s.updatedAt
    finalSummary.value = s.finalSummary || null
  }

  async function fetchGameState(evtId) {
    const { data } = await fetchState(evtId)
    applySnapshot(data)
  }

  function $reset() {
    eventId.value = null
    eventStatus.value = ''
    eventResult.value = null
    villainName.value = ''
    villainType.value = null
    villainHpCurrent.value = 0
    villainHpMax.value = 0
    schemeThreatCurrent.value = 0
    schemeThreatTarget.value = 0
    timerRemainingSeconds.value = 0
    timerStatus.value = 'NOT_CONFIGURED'
    totalReportedVillainDamage.value = 0
    startedAt.value = null
    finishedAt.value = null
    updatedAt.value = null
    finalSummary.value = null
  }

  return {
    eventId,
    eventStatus,
    eventResult,
    villainName,
    villainType,
    villainHpCurrent,
    villainHpMax,
    schemeThreatCurrent,
    schemeThreatTarget,
    timerRemainingSeconds,
    timerStatus,
    totalReportedVillainDamage,
    startedAt,
    finishedAt,
    updatedAt,
    finalSummary,
    applySnapshot,
    fetchGameState,
    $reset,
  }
})
