import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { useWebSocket } from '../useWebSocket'
import { useGameStateStore } from '@/stores/gameState'
import { useNotificationsStore } from '@/stores/notifications'

const clientState = vi.hoisted(() => ({
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscriptions: new Map(),
  config: null,
}))

vi.mock('sockjs-client', () => ({
  default: vi.fn(() => ({ close: vi.fn() })),
}))

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(function MockClient(config) {
    clientState.config = config

    return {
      activate: clientState.activate,
      deactivate: clientState.deactivate,
      subscribe: vi.fn((destination, handler) => {
        clientState.subscriptions.set(destination, handler)
        return { unsubscribe: vi.fn() }
      }),
    }
  }),
}))

const Harness = defineComponent({
  template: '<div />',
  setup() {
    const socket = useWebSocket(42)
    socket.connect()
    return socket
  },
})

function buildSnapshot(overrides = {}) {
  return {
    eventId: 42,
    eventStatus: 'IN_PROGRESS',
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
    updatedAt: '2026-05-21T12:00:00Z',
    finalSummary: null,
    ...overrides,
  }
}

function mountHarness() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(Harness, {
    global: {
      plugins: [pinia],
    },
  })
}

function publish(destination, payload) {
  const handler = clientState.subscriptions.get(destination)
  if (!handler) {
    throw new Error(`Missing subscription for ${destination}`)
  }

  handler({ body: JSON.stringify(payload) })
}

describe('useWebSocket', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clientState.subscriptions.clear()
    clientState.config = null
  })

  it('translates inter-pod notifications and avoids duplicating them from the game-state channel', () => {
    const wrapper = mountHarness()
    const gameStore = useGameStateStore()
    const notifStore = useNotificationsStore()

    expect(clientState.activate).toHaveBeenCalledTimes(1)

    clientState.config.onConnect()

    const payload = {
      operation: 'INTER_POD_CARD_CREATED',
      snapshot: buildSnapshot({ villainHpCurrent: 37 }),
      notifications: ['INTER_POD_CARD_CREATED'],
      emittedAt: '2026-05-21T12:00:00Z',
    }

    publish('/topic/events/42/game-state', payload)
    expect(gameStore.villainHpCurrent).toBe(37)
    expect(notifStore.items).toHaveLength(0)

    publish('/topic/events/42/notifications', payload)
    expect(notifStore.items).toHaveLength(1)
    expect(notifStore.items[0].message).toBe('Ha aparecido una carta inter-nodo.')
    expect(notifStore.items[0].type).toBe('info')

    wrapper.unmount()
  })

  it('skips locally suppressed realtime inter-pod notifications', () => {
    const wrapper = mountHarness()
    const notifStore = useNotificationsStore()

    clientState.config.onConnect()
    notifStore.suppressRealtimeNotification('INTER_POD_CARD_CREATED', { targetPodId: 7 })

    publish('/topic/events/42/notifications', {
      notifications: ['INTER_POD_CARD_CREATED'],
      targetPodId: 7,
      emittedAt: '2026-05-21T12:01:00Z',
    })

    expect(notifStore.items).toHaveLength(0)

    wrapper.unmount()
  })

  it('reserves final result notifications for the dedicated game-finished channel and lets them expire', () => {
    vi.useFakeTimers()

    const wrapper = mountHarness()
    const notifStore = useNotificationsStore()

    clientState.config.onConnect()

    publish('/topic/events/42/notifications', {
      notifications: ['EVENT_FINISHED_THREAT_DEFEAT'],
      snapshot: buildSnapshot({
        eventStatus: 'FINISHED',
        eventResult: 'THREAT_DEFEAT',
        finalSummary: {
          result: 'THREAT_DEFEAT',
          cause: 'THREAT_TARGET_REACHED',
        },
      }),
      emittedAt: '2026-05-21T12:05:00Z',
    })
    expect(notifStore.items).toHaveLength(0)

    publish('/topic/events/42/game-finished', {
      snapshot: buildSnapshot({
        eventStatus: 'FINISHED',
        eventResult: 'THREAT_DEFEAT',
        finalSummary: {
          result: 'THREAT_DEFEAT',
          cause: 'THREAT_TARGET_REACHED',
        },
      }),
      emittedAt: '2026-05-21T12:05:00Z',
    })

    expect(notifStore.items).toHaveLength(1)
    expect(notifStore.items[0].message).toBe('Derrota - La amenaza ha alcanzado el límite')
    expect(notifStore.items[0].type).toBe('error')

    vi.advanceTimersByTime(11999)
    expect(notifStore.items).toHaveLength(1)

    vi.advanceTimersByTime(1)
    expect(notifStore.items).toHaveLength(0)

    wrapper.unmount()
    vi.useRealTimers()
  })
})
