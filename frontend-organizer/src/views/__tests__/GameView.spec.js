import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GameView from '@/views/GameView.vue'

const {
  getGameState,
  getEventStructure,
  assignGroupToPod,
  startGame,
  reportVillainDamage,
  reportThreat,
  manualUpdateVillainHp,
  manualUpdateThreat,
  startTimer,
  pauseTimer,
  resumeTimer,
  adjustTimer,
  connect,
  socketConnected,
  realtimeEvent,
} = vi.hoisted(() => ({
  getGameState: vi.fn(),
  getEventStructure: vi.fn(),
  assignGroupToPod: vi.fn(),
  startGame: vi.fn(),
  reportVillainDamage: vi.fn(),
  reportThreat: vi.fn(),
  manualUpdateVillainHp: vi.fn(),
  manualUpdateThreat: vi.fn(),
  startTimer: vi.fn(),
  pauseTimer: vi.fn(),
  resumeTimer: vi.fn(),
  adjustTimer: vi.fn(),
  connect: vi.fn(),
  socketConnected: { value: true },
  realtimeEvent: { value: null },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { eventId: 42 },
  }),
}))

vi.mock('@/api/setup', () => ({
  getEventStructure,
  assignGroupToPod,
}))

vi.mock('@/api/gameState', () => ({
  getGameState,
  startGame,
  reportVillainDamage,
  reportThreat,
  manualUpdateVillainHp,
  manualUpdateThreat,
  startTimer,
  pauseTimer,
  resumeTimer,
  adjustTimer,
}))

vi.mock('@/composables/useWebSocket', () => ({
  useWebSocket: () => ({
    connected: socketConnected.value,
    realtimeEvent,
    connect,
  }),
}))

function buildSnapshot(overrides = {}) {
  return {
    eventId: 42,
    eventStatus: 'READY',
    eventResult: null,
    villainName: 'Loki',
    villainType: 'TRICKSTER',
    villainHpCurrent: 40,
    villainHpMax: 60,
    schemeThreatCurrent: 3,
    schemeThreatTarget: 6,
    timerRemainingSeconds: 7200,
    timerStatus: 'RUNNING',
    totalReportedVillainDamage: 20,
    startedAt: null,
    finishedAt: null,
    updatedAt: '2026-05-11T12:00:00Z',
    finalSummary: null,
    ...overrides,
  }
}

function buildStructure(overrides = {}) {
  return {
    eventId: 42,
    eventName: 'Evento Loki',
    configuredPlayerCount: 4,
    currentGroupCount: 1,
    assignedPlayerCount: 4,
    unassignedGroupCount: 0,
    unassignedPlayerCount: 0,
    schemeThreatTarget: 6,
    suggestedPodName: 'Pod 2',
    pods: [
      {
        id: 7,
        name: 'Pod Norte',
        sortOrder: 0,
        totalGroups: 1,
        totalPlayers: 4,
        warnings: [],
        groups: [{ id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 4 }],
      },
    ],
    unassignedGroups: [],
    ...overrides,
  }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(GameView, {
    global: {
      plugins: [pinia],
      stubs: {
        VillainHpBar: { template: '<div class="villain-hp-bar-stub" />' },
        ThreatBar: { template: '<div class="threat-bar-stub" />' },
        GameTimer: { template: '<div class="game-timer-stub" />' },
        FinalSummary: { template: '<div class="final-summary-stub" />' },
        InterPodAlerts: { template: '<div class="inter-pod-alerts-stub" />' },
      },
    },
  })
}

describe('GameView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    socketConnected.value = true
    realtimeEvent.value = null
    getGameState.mockResolvedValue({ data: buildSnapshot() })
    getEventStructure.mockResolvedValue({ data: buildStructure() })
    assignGroupToPod.mockResolvedValue({ data: {} })
    startGame.mockResolvedValue({ data: { snapshot: buildSnapshot({ eventStatus: 'IN_PROGRESS' }) } })
    reportVillainDamage.mockResolvedValue({ data: { snapshot: buildSnapshot({ villainHpCurrent: 39 }) } })
    reportThreat.mockResolvedValue({ data: { snapshot: buildSnapshot({ schemeThreatCurrent: 4 }) } })
    manualUpdateVillainHp.mockResolvedValue({ data: { snapshot: buildSnapshot() } })
    manualUpdateThreat.mockResolvedValue({ data: { snapshot: buildSnapshot() } })
    startTimer.mockResolvedValue({ data: { snapshot: buildSnapshot() } })
    pauseTimer.mockResolvedValue({ data: { snapshot: buildSnapshot({ timerStatus: 'PAUSED' }) } })
    resumeTimer.mockResolvedValue({ data: { snapshot: buildSnapshot() } })
    adjustTimer.mockResolvedValue({ data: { snapshot: buildSnapshot() } })
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('loads game state and structure on mount', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getGameState).toHaveBeenCalledWith(42)
    expect(getEventStructure).toHaveBeenCalledWith(42)
    expect(connect).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Panel de Partida')

    wrapper.unmount()
  })

  it('does not poll structure on a timer', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getEventStructure).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(15000)
    await flushPromises()

    expect(getEventStructure).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })
})