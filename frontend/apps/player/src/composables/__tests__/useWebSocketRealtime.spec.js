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
})
