import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent, nextTick, ref } from 'vue'
import { useWebSocket } from '../useWebSocket'
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

const podId = ref(null)
const podScopeReady = ref(false)

const Harness = defineComponent({
  template: '<div />',
  setup() {
    const socket = useWebSocket(42, { podId, podScopeReady })
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
    updatedAt: '2026-05-21T18:00:00Z',
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

describe('player useWebSocket realtime filtering', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clientState.subscriptions.clear()
    clientState.config = null
    podId.value = null
    podScopeReady.value = false
  })

  it('queues pod-scoped events until the player pod is known and discards foreign pod notifications', async () => {
    const wrapper = mountHarness()
    const notifStore = useNotificationsStore()

    clientState.config.onConnect()

    publish('/topic/events/42/game-state', {
      operation: 'INTER_POD_CARD_CREATED',
      notifications: ['INTER_POD_CARD_CREATED'],
      targetPodId: 8,
      emittedAt: '2026-05-21T18:00:00Z',
    })

    publish('/topic/events/42/game-state', {
      operation: 'INTER_POD_CARD_DAMAGED',
      notifications: ['INTER_POD_CARD_DAMAGED'],
      targetPodId: 7,
      emittedAt: '2026-05-21T18:00:01Z',
    })

    expect(notifStore.items).toHaveLength(0)

    podId.value = 7
    podScopeReady.value = true
    await nextTick()

    expect(notifStore.items).toHaveLength(1)
    expect(notifStore.items[0].message).toBe('Una carta inter-nodo de tu pod ha recibido daño.')

    wrapper.unmount()
  })

  it('shows global notifications immediately even with pod filtering enabled', () => {
    const wrapper = mountHarness()
    const notifStore = useNotificationsStore()

    clientState.config.onConnect()
    podScopeReady.value = true

    publish('/topic/events/42/game-state', {
      operation: 'REPORT_THREAT',
      notifications: ['THREAT_50_PERCENT'],
      emittedAt: '2026-05-21T18:05:00Z',
    })

    expect(notifStore.items).toHaveLength(1)
    expect(notifStore.items[0].message).toBe('¡Amenaza al 50%!')

    wrapper.unmount()
  })

  it('skips locally suppressed pod-scoped realtime notifications', async () => {
    const wrapper = mountHarness()
    const notifStore = useNotificationsStore()

    clientState.config.onConnect()
    podId.value = 7
    podScopeReady.value = true
    await nextTick()

    notifStore.suppressRealtimeNotification('INTER_POD_CARD_DAMAGED', { targetPodId: 7 })

    publish('/topic/events/42/game-state', {
      operation: 'INTER_POD_CARD_DAMAGED',
      snapshot: buildSnapshot(),
      notifications: ['INTER_POD_CARD_DAMAGED'],
      targetPodId: 7,
      emittedAt: '2026-05-21T18:06:00Z',
    })

    expect(notifStore.items).toHaveLength(0)

    wrapper.unmount()
  })

  it('shows final results with resolved severity and finite timeout', () => {
    vi.useFakeTimers()

    const wrapper = mountHarness()
    const notifStore = useNotificationsStore()

    clientState.config.onConnect()

    publish('/topic/events/42/game-finished', {
      snapshot: buildSnapshot({
        eventStatus: 'FINISHED',
        eventResult: 'PLAYERS_VICTORY',
        finishedAt: '2026-05-21T18:07:00Z',
        finalSummary: {
          result: 'PLAYERS_VICTORY',
          cause: 'VILLAIN_HP_ZERO',
        },
      }),
      emittedAt: '2026-05-21T18:07:00Z',
    })

    expect(notifStore.items).toHaveLength(1)
    expect(notifStore.items[0].message).toBe('¡Victoria! ¡El villano ha sido derrotado!')
    expect(notifStore.items[0].type).toBe('success')

    vi.advanceTimersByTime(12000)
    expect(notifStore.items).toHaveLength(0)

    wrapper.unmount()
    vi.useRealTimers()
  })
})
