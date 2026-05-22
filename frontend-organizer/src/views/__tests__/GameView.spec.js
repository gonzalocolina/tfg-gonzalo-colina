import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GameView from '@/views/GameView.vue'
import { useGameStateStore } from '@/stores/gameState'

const {
  getGameState,
  getEventStructure,
  assignGroupToPod,
  startGame,
  delegateVillainDamage,
  delegateRemoveVillainDamage,
  delegateThreat,
  delegateRemoveThreat,
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
  delegateVillainDamage: vi.fn(),
  delegateRemoveVillainDamage: vi.fn(),
  delegateThreat: vi.fn(),
  delegateRemoveThreat: vi.fn(),
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
  delegateVillainDamage,
  delegateRemoveVillainDamage,
  delegateThreat,
  delegateRemoveThreat,
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
        name: 'Pod 1',
        sortOrder: 0,
        totalGroups: 1,
        totalPlayers: 4,
        warnings: [],
        groups: [
          {
            id: 19,
            podId: 7,
            identifier: 'Mesa Alfa',
            playerCount: 4,
            representativeCode: 'REP-ALFA',
            observerCode: 'OBS-ALFA',
          },
        ],
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
        RouterLink: { template: '<a><slot /></a>' },
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
    delegateVillainDamage.mockResolvedValue({ data: { snapshot: buildSnapshot({ villainHpCurrent: 39 }) } })
    delegateRemoveVillainDamage.mockResolvedValue({ data: { snapshot: buildSnapshot({ villainHpCurrent: 41 }) } })
    delegateThreat.mockResolvedValue({ data: { snapshot: buildSnapshot({ schemeThreatCurrent: 4 }) } })
    delegateRemoveThreat.mockResolvedValue({ data: { snapshot: buildSnapshot({ schemeThreatCurrent: 2 }) } })
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

  it('allows delegated registration, delegated reversals, and exact global corrections without justification text', async () => {
    getGameState.mockResolvedValue({ data: buildSnapshot({ eventStatus: 'IN_PROGRESS' }) })
    getEventStructure.mockResolvedValue({
      data: buildStructure({
        currentGroupCount: 2,
        configuredPlayerCount: 6,
        assignedPlayerCount: 4,
        unassignedGroupCount: 1,
        unassignedPlayerCount: 2,
        unassignedGroups: [
          {
            id: 21,
            podId: null,
            identifier: 'Mesa Beta',
            playerCount: 2,
            representativeCode: 'REP-BETA',
            observerCode: 'OBS-BETA',
          },
        ],
      }),
    })
    delegateVillainDamage.mockResolvedValue({
      data: { snapshot: buildSnapshot({ eventStatus: 'IN_PROGRESS', villainHpCurrent: 39 }) },
    })
    delegateRemoveVillainDamage.mockResolvedValue({
      data: { snapshot: buildSnapshot({ eventStatus: 'IN_PROGRESS', villainHpCurrent: 42 }) },
    })
    delegateThreat.mockResolvedValue({
      data: { snapshot: buildSnapshot({ eventStatus: 'IN_PROGRESS', schemeThreatCurrent: 4 }) },
    })
    delegateRemoveThreat.mockResolvedValue({
      data: { snapshot: buildSnapshot({ eventStatus: 'IN_PROGRESS', schemeThreatCurrent: 1 }) },
    })
    manualUpdateVillainHp.mockResolvedValue({
      data: { snapshot: buildSnapshot({ eventStatus: 'IN_PROGRESS', villainHpCurrent: 35 }) },
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Registro delegado en nombre de una mesa')
    expect(wrapper.text()).toContain('Correcciones manuales globales')
    expect(wrapper.text()).not.toContain('Código representante')

    const damageGroupSelect = wrapper.find('#delegated-dmg-group')
    const threatGroupSelect = wrapper.find('#delegated-threat-group')

    expect(damageGroupSelect.exists()).toBe(true)
    expect(threatGroupSelect.exists()).toBe(true)
    expect(damageGroupSelect.text()).toContain('Mesa Alfa · Pod 1')
    expect(damageGroupSelect.text()).toContain('Mesa Beta · Sin pod asignado')

    await damageGroupSelect.setValue('19')
    await wrapper.find('#delegated-dmg-amount').setValue('3')
    await wrapper.find('[data-testid="delegated-damage-form"]').trigger('submit')
    await flushPromises()

    expect(delegateVillainDamage).toHaveBeenCalledWith(42, {
      groupId: 19,
      damage: 3,
      justification: '',
    })

    await wrapper.find('#delegated-dmg-amount').setValue('2')
    const removeDamageButton = wrapper.findAll('button').find((button) => button.text().includes('Revertir daño delegado'))
    expect(removeDamageButton).toBeDefined()
    await removeDamageButton.trigger('click')
    await flushPromises()

    expect(delegateRemoveVillainDamage).toHaveBeenCalledWith(42, {
      groupId: 19,
      damage: 2,
      justification: '',
    })

    await threatGroupSelect.setValue('21')
    await wrapper.find('#delegated-threat-amount').setValue('2')
    await wrapper.find('[data-testid="delegated-threat-form"]').trigger('submit')
    await flushPromises()

    expect(delegateThreat).toHaveBeenCalledWith(42, {
      groupId: 21,
      threat: 2,
      justification: '',
    })

    await wrapper.find('#delegated-threat-amount').setValue('2')
    const removeThreatButton = wrapper.findAll('button').find((button) => button.text().includes('Retirar amenaza delegada'))
    expect(removeThreatButton).toBeDefined()
    await removeThreatButton.trigger('click')
    await flushPromises()

    expect(delegateRemoveThreat).toHaveBeenCalledWith(42, {
      groupId: 21,
      threat: 2,
      justification: '',
    })

    await wrapper.find('#manual-hp').setValue('35')
    await wrapper.find('[data-testid="manual-hp-form"]').trigger('submit')
    await flushPromises()

    expect(manualUpdateVillainHp).toHaveBeenCalledWith(42, {
      value: 35,
      justification: '',
    })

    await wrapper.find('#manual-threat').setValue('2')
    await wrapper.find('[data-testid="manual-threat-form"]').trigger('submit')
    await flushPromises()

    expect(manualUpdateThreat).toHaveBeenCalledWith(42, {
      value: 2,
      justification: '',
    })

    wrapper.unmount()
  })

  it('shows delegated correction tools after a finish by hp or threat, but keeps global corrections hidden', async () => {
    getGameState.mockResolvedValue({
      data: buildSnapshot({
        eventStatus: 'FINISHED',
        eventResult: 'THREAT_DEFEAT',
        finalSummary: {
          result: 'THREAT_DEFEAT',
          cause: 'THREAT_TARGET_REACHED',
          durationSeconds: 1800,
          totalReportedVillainDamage: 20,
          finalThreat: 6,
        },
      }),
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Registro delegado en nombre de una mesa')
    expect(wrapper.text()).toContain('Revertir daño delegado')
    expect(wrapper.text()).not.toContain('Correcciones manuales globales')

    wrapper.unmount()
  })

  it('labels the shared scheme as the main plan', async () => {
    getGameState.mockResolvedValue({ data: buildSnapshot({ eventStatus: 'IN_PROGRESS' }) })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Amenaza del Plan Principal')

    wrapper.unmount()
  })

  it('keeps manual correction values synced with live state updates without stomping focused edits', async () => {
    getGameState.mockResolvedValue({
      data: buildSnapshot({ eventStatus: 'IN_PROGRESS', villainHpCurrent: 40, schemeThreatCurrent: 3 }) },
    )

    const wrapper = mountView()
    await flushPromises()

    const hpInput = wrapper.get('#manual-hp')
    const threatInput = wrapper.get('#manual-threat')

    expect(hpInput.element.value).toBe('40')
    expect(threatInput.element.value).toBe('3')

    await hpInput.trigger('focus')
    await hpInput.setValue('41')

    const gameStore = useGameStateStore()
    gameStore.applySnapshot(buildSnapshot({ eventStatus: 'IN_PROGRESS', villainHpCurrent: 37, schemeThreatCurrent: 5 }))
    await flushPromises()

    expect(hpInput.element.value).toBe('41')
    expect(threatInput.element.value).toBe('5')

    await hpInput.trigger('blur')
    gameStore.applySnapshot(buildSnapshot({ eventStatus: 'IN_PROGRESS', villainHpCurrent: 36, schemeThreatCurrent: 4 }))
    await flushPromises()

    expect(hpInput.element.value).toBe('36')
    expect(threatInput.element.value).toBe('4')

    wrapper.unmount()
  })
})