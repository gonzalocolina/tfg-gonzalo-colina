import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PlayerGameView from '../PlayerGameView.vue'

const { getGameState, getEventStructure, getInterPodCards, connect, syncImageUrls, protectedImagesById } = vi.hoisted(() => ({
  getGameState: vi.fn(),
  getEventStructure: vi.fn(),
  getInterPodCards: vi.fn(),
  connect: vi.fn(),
  syncImageUrls: vi.fn(),
  protectedImagesById: {},
}))

let websocketConnected = true

vi.mock('@/api/gameState', () => ({
  getGameState,
}))

vi.mock('@/api/setup', () => ({
  getEventStructure,
}))

vi.mock('@/api/interPod', () => ({
  getInterPodCards,
}))

vi.mock('@/composables/useWebSocket', () => ({
  useWebSocket: () => ({
    connected: websocketConnected,
    connect,
  }),
}))

vi.mock('@/composables/useProtectedImageUrls', () => ({
  useProtectedImageUrls: () => ({
    protectedImageUrls: {},
    getProtectedImageUrl: (id) => protectedImagesById[String(id)] || null,
    syncImageUrls,
    revokeImage: vi.fn(),
    revokeAll: vi.fn(),
  }),
}))

function buildSnapshot(eventStatus = 'READY') {
  return {
    eventId: 1,
    eventStatus,
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
    updatedAt: '2026-03-31T12:00:00Z',
    finalSummary: null,
  }
}

function buildStructure(overrides = {}) {
  return {
    eventId: 1,
    eventName: 'Epic Loki',
    configuredPlayerCount: 9,
    currentGroupCount: 3,
    assignedPlayerCount: 9,
    unassignedGroupCount: 0,
    unassignedPlayerCount: 0,
    schemeThreatTarget: 6,
    suggestedPodName: 'Pod 1',
    pods: [
      {
        id: 7,
        name: 'Pod Norte',
        totalGroups: 2,
        totalPlayers: 5,
        warnings: [],
        groups: [
          { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 },
          { id: 20, podId: 7, identifier: 'Mesa Beta', playerCount: 3 },
        ],
      },
      {
        id: 8,
        name: 'Pod Sur',
        totalGroups: 1,
        totalPlayers: 4,
        warnings: ['La diferencia de jugadores entre mesas del pod es superior a 1.'],
        groups: [
          { id: 21, podId: 8, identifier: 'Mesa Gamma', playerCount: 4 },
        ],
      },
    ],
    unassignedGroups: [],
    ...overrides,
  }
}

function seedAuthSession(role = 'GROUP_REPRESENTATIVE') {
  sessionStorage.setItem(
    'auth-player',
    JSON.stringify({
      accessToken: 'tok-123',
      role,
      eventId: 1,
      groupId: 20,
      groupLabel: 'Mesa Beta',
      code: 'REP001',
    }),
  )
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(PlayerGameView, {
    global: {
      plugins: [pinia],
      stubs: {
        PlayerHeader: {
          props: ['eventName', 'groupLabel', 'connected'],
          template: '<div class="header-stub">{{ groupLabel }} · {{ eventName }} · {{ connected }}</div>',
        },
        VillainHpCard: { template: '<div class="villain-card-stub">HP</div>' },
        ThreatCard: { template: '<div class="threat-card-stub">THREAT</div>' },
        TimerDisplay: { template: '<div class="timer-stub">TIMER</div>' },
        FinalSummary: { template: '<div class="summary-stub" />' },
        ReportDamageDialog: { template: '<div class="damage-dialog-stub" />' },
        ReportThreatDialog: { template: '<div class="threat-dialog-stub" />' },
        InterPodAlertDialog: {
          props: ['podId', 'podName', 'initialCardId'],
          template: '<div class="inter-pod-dialog-stub">Pod {{ podId }} · {{ podName }} · Card {{ initialCardId ?? "none" }}</div>',
        },
      },
    },
  })
}

describe('PlayerGameView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    sessionStorage.clear()
    websocketConnected = true
    connect.mockReset()
    getGameState.mockReset()
    getEventStructure.mockReset()
    getInterPodCards.mockReset()
    syncImageUrls.mockReset()
    Object.keys(protectedImagesById).forEach((key) => delete protectedImagesById[key])
    getGameState.mockResolvedValue({ data: buildSnapshot() })
    getEventStructure.mockResolvedValue({ data: buildStructure() })
    getInterPodCards.mockResolvedValue({ data: [] })
    syncImageUrls.mockResolvedValue()
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
    sessionStorage.clear()
  })

  it('shows pods and highlights the current table while waiting', async () => {
    seedAuthSession('GROUP_OBSERVER')
    const wrapper = mountView()

    await flushPromises()

    expect(getGameState).toHaveBeenCalledWith(1)
    expect(getEventStructure).toHaveBeenCalledWith(1)
    expect(connect).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Esperando a que el organizador inicie la partida…')
    expect(wrapper.find('.header-stub').text()).toContain('Mesa Beta · Epic Loki')
    expect(wrapper.text()).toContain('Pod Norte')
    expect(wrapper.find('.pod-overview-card__group--current').text()).toContain('Mesa Beta')

    wrapper.unmount()
  })

  it('passes the current pod to the inter-pod dialog when the representative opens it', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    getGameState.mockResolvedValue({ data: buildSnapshot('IN_PROGRESS') })
    const wrapper = mountView()

    await flushPromises()
    await wrapper.get('.action-bar .btn-secondary').trigger('click')

    expect(wrapper.find('.inter-pod-dialog-stub').text()).toContain('Pod 7 · Pod Norte')

    wrapper.unmount()
  })

  it('shows inline inter-pod cards with the protected image helper and the correct cardName field', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    getGameState.mockResolvedValue({ data: buildSnapshot('IN_PROGRESS') })
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 77,
          cardName: 'THE_MANGOG',
          status: 'REVELADA',
          hpCurrent: 6,
          hpMax: 8,
          imageUrl: '/api/events/1/inter-pod-cards/77/image',
        },
      ],
    })
    protectedImagesById['77'] = 'blob:player-card-77'

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(getInterPodCards).toHaveBeenCalledWith(1, 7)
    expect(syncImageUrls).toHaveBeenCalledWith([
      { id: 77, url: '/api/events/1/inter-pod-cards/77/image' },
    ])
    expect(wrapper.text()).toContain('The Mangog')

    const image = wrapper.find('.interpod-inline-img')
    expect(image.exists()).toBe(true)
    expect(image.attributes('src')).toBe('blob:player-card-77')
    expect(wrapper.html().indexOf('THREAT')).toBeLessThan(wrapper.html().indexOf('Cartas Inter-Pod activas'))
    expect(wrapper.html().indexOf('Cartas Inter-Pod activas')).toBeLessThan(wrapper.html().indexOf('Pods y mesas'))

    wrapper.unmount()
  })

  it('opens the inter-pod detail dialog focused on the tapped card', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    getGameState.mockResolvedValue({ data: buildSnapshot('IN_PROGRESS') })
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 77,
          cardName: 'THE_MANGOG',
          status: 'REVELADA',
          hpCurrent: 6,
          hpMax: 8,
          imageUrl: '/api/events/1/inter-pod-cards/77/image',
        },
      ],
    })

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    await wrapper.get('[data-testid="player-interpod-card-77"]').trigger('click')

    expect(wrapper.find('.inter-pod-dialog-stub').text()).toContain('Pod 7 · Pod Norte · Card 77')

    wrapper.unmount()
  })

  it('shows a pause warning and hides the action bar while the game is paused', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    getGameState.mockResolvedValue({
      data: {
        ...buildSnapshot('IN_PROGRESS'),
        timerStatus: 'PAUSED',
      },
    })
    const wrapper = mountView()

    await flushPromises()

    expect(wrapper.text()).toContain('Partida pausada')
    expect(wrapper.find('.action-bar').exists()).toBe(false)
    expect(wrapper.html().indexOf('TIMER')).toBeLessThan(wrapper.html().indexOf('HP'))
    expect(wrapper.html().indexOf('HP')).toBeLessThan(wrapper.html().indexOf('THREAT'))

    wrapper.unmount()
  })

  it('shows a timer-start warning and hides the action bar while the counter is pending', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    getGameState.mockResolvedValue({
      data: {
        ...buildSnapshot('IN_PROGRESS'),
        timerStatus: 'READY',
      },
    })
    const wrapper = mountView()

    await flushPromises()

    expect(wrapper.text()).toContain('Contador pendiente de inicio')
    expect(wrapper.text()).toContain('todavía no ha iniciado el contador')
    expect(wrapper.find('.action-bar').exists()).toBe(false)

    wrapper.unmount()
  })

  it('shows the pending assignment state when the current table has no pod yet', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    getGameState.mockResolvedValue({ data: buildSnapshot('IN_PROGRESS') })
    getEventStructure.mockResolvedValue({
      data: buildStructure({
        currentGroupCount: 4,
        assignedPlayerCount: 5,
        unassignedGroupCount: 1,
        unassignedPlayerCount: 3,
        pods: [
          {
            id: 7,
            name: 'Pod Norte',
            totalGroups: 1,
            totalPlayers: 2,
            warnings: [],
            groups: [{ id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 }],
          },
        ],
        unassignedGroups: [{ id: 20, podId: null, identifier: 'Mesa Beta', playerCount: 3 }],
      }),
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Tu mesa aún no está asignada a un pod')
    expect(wrapper.text()).toContain('Mesas pendientes de asignación')
    expect(wrapper.find('.action-bar').exists()).toBe(false)

    wrapper.unmount()
  })

  it('polls the game state when realtime is disconnected', async () => {
    seedAuthSession('GROUP_REPRESENTATIVE')
    websocketConnected = false

    const wrapper = mountView()
    await flushPromises()

    expect(getGameState).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(4000)
    await flushPromises()

    expect(getGameState).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })
})
