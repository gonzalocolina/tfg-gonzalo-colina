import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EventSetupView from '@/views/EventSetupView.vue'
import { __websocketState } from '@/composables/useWebSocket'

const { getEventStructure, createPod, assignGroupToPod, updateGroup, updatePod, deletePod } = vi.hoisted(() => ({
  getEventStructure: vi.fn(),
  createPod: vi.fn(),
  assignGroupToPod: vi.fn(),
  updateGroup: vi.fn(),
  updatePod: vi.fn(),
  deletePod: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { eventId: 42 },
  }),
}))

vi.mock('@/api/setup', () => ({
  getEventStructure,
  createPod,
  assignGroupToPod,
  updateGroup,
  updatePod,
  deletePod,
}))

vi.mock('@/composables/useWebSocket', async () => {
  const { ref } = await import('vue')

  const websocketState = {
    connected: ref(false),
    realtimeEvent: ref(null),
    connect: vi.fn(),
  }

  return {
    __websocketState: websocketState,
    useWebSocket: () => websocketState,
  }
})

function buildStructure(overrides = {}) {
  return {
    eventId: 42,
    eventName: 'Evento Setup',
    configuredPlayerCount: 8,
    currentGroupCount: 3,
    assignedPlayerCount: 5,
    unassignedGroupCount: 1,
    unassignedPlayerCount: 3,
    schemeThreatTarget: 6,
    suggestedPodName: 'Nodo 2',
    pods: [
      {
        id: 7,
        name: 'Nodo 1',
        sortOrder: 0,
        totalGroups: 2,
        totalPlayers: 5,
        warnings: [],
        groups: [
          { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 },
          { id: 21, podId: 7, identifier: 'Mesa Gamma', playerCount: 3 },
        ],
      },
    ],
    unassignedGroups: [
      { id: 20, podId: null, identifier: 'Mesa Beta', playerCount: 3 },
    ],
    ...overrides,
  }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(EventSetupView, {
    global: {
      plugins: [pinia],
    },
  })
}

function createDragData() {
  return {
    setData: vi.fn(),
    effectAllowed: 'move',
  }
}

describe('EventSetupView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    __websocketState.connected.value = false
    __websocketState.realtimeEvent.value = null
    __websocketState.connect.mockClear()
    getEventStructure.mockResolvedValue({ data: buildStructure() })
    createPod.mockResolvedValue({ data: { id: 8, name: 'Nodo 2', sortOrder: 1 } })
    assignGroupToPod.mockResolvedValue({ data: { id: 20, podId: 7 } })
    updateGroup.mockResolvedValue({ data: { id: 20 } })
    updatePod.mockResolvedValue({ data: { id: 7 } })
    deletePod.mockResolvedValue({ data: {} })
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('renders the new board layout without representative or observer codes', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getEventStructure).toHaveBeenCalledWith(42)
    expect(__websocketState.connect).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Nuevo nodo')
    expect(wrapper.text()).toContain('Reparto de mesas')
    expect(wrapper.text()).toContain('Sin asignar')
    expect(wrapper.text()).toContain('Mesa Beta')
    expect(wrapper.get('#newPodName').element.value).toBe('Nodo 2')
    expect(wrapper.text()).not.toContain('Código Representante')
    expect(wrapper.text()).not.toContain('Código Observador')
    expect(wrapper.find('[data-testid="create-pod-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unassigned-column"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('does not auto-refresh the structure on a timer', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getEventStructure).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(12000)
    await flushPromises()

    expect(getEventStructure).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('refreshes the structure when a websocket update arrives', async () => {
    getEventStructure
      .mockResolvedValueOnce({ data: buildStructure() })
      .mockResolvedValueOnce({
        data: buildStructure({
          currentGroupCount: 4,
          configuredPlayerCount: 10,
          assignedPlayerCount: 5,
          unassignedGroupCount: 2,
          unassignedPlayerCount: 5,
          unassignedGroups: [
            { id: 20, podId: null, identifier: 'Mesa Beta', playerCount: 3 },
            { id: 22, podId: null, identifier: 'Mesa Delta', playerCount: 2 },
          ],
        }),
      })

    const wrapper = mountView()
    await flushPromises()

    __websocketState.realtimeEvent.value = {
      operation: 'EVENT_STRUCTURE_UPDATED',
      notifications: [],
      emittedAt: Date.now(),
    }
    await flushPromises()
    await flushPromises()

    expect(getEventStructure.mock.calls.length).toBeGreaterThanOrEqual(2)
    expect(wrapper.text()).toContain('Mesa Delta')
    expect(wrapper.text()).toContain('10')

    wrapper.unmount()
  })

  it('moves a pending table into a pod with drag and drop', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="group-item-20"]').trigger('dragstart', { dataTransfer: createDragData() })
    await wrapper.get('[data-testid="pod-column-7"]').trigger('dragover')
    await wrapper.get('[data-testid="pod-column-7"]').trigger('drop')
    await flushPromises()

    expect(assignGroupToPod).toHaveBeenCalledWith(42, 20, 7)

    wrapper.unmount()
  })

  it('returns a table to unassigned when dropped on the first column', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="group-item-19"]').trigger('dragstart', { dataTransfer: createDragData() })
    await wrapper.get('[data-testid="unassigned-column"]').trigger('dragover')
    await wrapper.get('[data-testid="unassigned-column"]').trigger('drop')
    await flushPromises()

    expect(assignGroupToPod).toHaveBeenCalledWith(42, 19, null)

    wrapper.unmount()
  })

  it('opens table editing from the pencil button and updates name and players', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="group-edit-button-20"]').trigger('click')
    await wrapper.get('#group-identifier-20').setValue('Mesa Beta Prime')
    await wrapper.get('#group-player-count-20').setValue('4')
    await wrapper.get('[data-testid="group-item-20"] .btn.btn-primary').trigger('click')
    await flushPromises()

    expect(updateGroup).toHaveBeenCalledWith(42, 20, {
      identifier: 'Mesa Beta Prime',
      playerCount: 4,
    })

    wrapper.unmount()
  })

  it('reorganizes all tables into an optimal balanced distribution and creates pods when needed', async () => {
    getEventStructure.mockResolvedValue({
      data: buildStructure({
        configuredPlayerCount: 24,
        currentGroupCount: 9,
        assignedPlayerCount: 21,
        unassignedGroupCount: 1,
        unassignedPlayerCount: 3,
        suggestedPodName: 'Nodo 3',
        pods: [
          {
            id: 7,
            name: 'Nodo 1',
            sortOrder: 0,
            totalGroups: 4,
            totalPlayers: 10,
            warnings: [],
            groups: [
              { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 },
              { id: 21, podId: 7, identifier: 'Mesa Beta', playerCount: 3 },
              { id: 22, podId: 7, identifier: 'Mesa Gamma', playerCount: 2 },
              { id: 23, podId: 7, identifier: 'Mesa Delta', playerCount: 3 },
            ],
          },
          {
            id: 8,
            name: 'Nodo 2',
            sortOrder: 1,
            totalGroups: 4,
            totalPlayers: 11,
            warnings: [],
            groups: [
              { id: 24, podId: 8, identifier: 'Mesa Epsilon', playerCount: 2 },
              { id: 25, podId: 8, identifier: 'Mesa Zeta', playerCount: 3 },
              { id: 26, podId: 8, identifier: 'Mesa Eta', playerCount: 3 },
              { id: 27, podId: 8, identifier: 'Mesa Theta', playerCount: 3 },
            ],
          },
        ],
        unassignedGroups: [
          { id: 20, podId: null, identifier: 'Mesa Iota', playerCount: 3 },
        ],
      }),
    })
    createPod.mockResolvedValueOnce({ data: { id: 9, name: 'Nodo 3', sortOrder: 2 } })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="auto-rebalance-button"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(createPod).toHaveBeenCalledWith(42, { name: 'Nodo 3', sortOrder: 2 })
    expect(assignGroupToPod).toHaveBeenCalledTimes(4)
    expect(assignGroupToPod).toHaveBeenNthCalledWith(1, 42, 23, 8)
    expect(assignGroupToPod).toHaveBeenNthCalledWith(2, 42, 26, 9)
    expect(assignGroupToPod).toHaveBeenNthCalledWith(3, 42, 27, 9)
    expect(assignGroupToPod).toHaveBeenNthCalledWith(4, 42, 20, 9)
    expect(deletePod).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('assigns only pending tables and creates a new pod when that avoids overloading existing ones', async () => {
    getEventStructure.mockResolvedValue({
      data: buildStructure({
        configuredPlayerCount: 16,
        currentGroupCount: 6,
        assignedPlayerCount: 11,
        unassignedGroupCount: 2,
        unassignedPlayerCount: 5,
        suggestedPodName: 'Nodo 2',
        pods: [
          {
            id: 7,
            name: 'Nodo 1',
            sortOrder: 0,
            totalGroups: 4,
            totalPlayers: 11,
            warnings: [],
            groups: [
              { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 },
              { id: 21, podId: 7, identifier: 'Mesa Beta', playerCount: 3 },
              { id: 22, podId: 7, identifier: 'Mesa Gamma', playerCount: 3 },
              { id: 23, podId: 7, identifier: 'Mesa Delta', playerCount: 3 },
            ],
          },
        ],
        unassignedGroups: [
          { id: 20, podId: null, identifier: 'Mesa Epsilon', playerCount: 2 },
          { id: 24, podId: null, identifier: 'Mesa Zeta', playerCount: 3 },
        ],
      }),
    })
    createPod.mockResolvedValueOnce({ data: { id: 8, name: 'Nodo 2', sortOrder: 1 } })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="auto-assign-pending-button"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(createPod).toHaveBeenCalledWith(42, { name: 'Nodo 2', sortOrder: 1 })
    expect(assignGroupToPod).toHaveBeenCalledTimes(2)
    expect(assignGroupToPod).toHaveBeenNthCalledWith(1, 42, 20, 8)
    expect(assignGroupToPod).toHaveBeenNthCalledWith(2, 42, 24, 8)
    expect(assignGroupToPod.mock.calls.every(([, groupId]) => [20, 24].includes(groupId))).toBe(true)

    wrapper.unmount()
  })

  it('assigns a single pending table to an existing pod instead of creating a nearly empty new pod', async () => {
    getEventStructure.mockResolvedValue({
      data: buildStructure({
        configuredPlayerCount: 18,
        currentGroupCount: 9,
        assignedPlayerCount: 16,
        unassignedGroupCount: 1,
        unassignedPlayerCount: 2,
        suggestedPodName: 'Nodo 3',
        pods: [
          {
            id: 7,
            name: 'Nodo 1',
            sortOrder: 0,
            totalGroups: 4,
            totalPlayers: 8,
            warnings: [],
            groups: [
              { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 },
              { id: 21, podId: 7, identifier: 'Mesa Beta', playerCount: 2 },
              { id: 22, podId: 7, identifier: 'Mesa Gamma', playerCount: 2 },
              { id: 23, podId: 7, identifier: 'Mesa Delta', playerCount: 2 },
            ],
          },
          {
            id: 8,
            name: 'Nodo 2',
            sortOrder: 1,
            totalGroups: 4,
            totalPlayers: 8,
            warnings: [],
            groups: [
              { id: 24, podId: 8, identifier: 'Mesa Epsilon', playerCount: 2 },
              { id: 25, podId: 8, identifier: 'Mesa Zeta', playerCount: 2 },
              { id: 26, podId: 8, identifier: 'Mesa Eta', playerCount: 2 },
              { id: 27, podId: 8, identifier: 'Mesa Theta', playerCount: 2 },
            ],
          },
        ],
        unassignedGroups: [
          { id: 20, podId: null, identifier: 'Mesa Iota', playerCount: 2 },
        ],
      }),
    })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="auto-assign-pending-button"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(createPod).not.toHaveBeenCalled()
    expect(assignGroupToPod).toHaveBeenCalledWith(42, 20, 7)

    wrapper.unmount()
  })

  it('removes surplus pods when a full rebalance can consolidate the tables', async () => {
    getEventStructure.mockResolvedValue({
      data: buildStructure({
        configuredPlayerCount: 4,
        currentGroupCount: 2,
        assignedPlayerCount: 4,
        unassignedGroupCount: 0,
        unassignedPlayerCount: 0,
        suggestedPodName: 'Nodo 3',
        pods: [
          {
            id: 7,
            name: 'Nodo 1',
            sortOrder: 0,
            totalGroups: 1,
            totalPlayers: 2,
            warnings: [],
            groups: [
              { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2 },
            ],
          },
          {
            id: 8,
            name: 'Nodo 2',
            sortOrder: 1,
            totalGroups: 1,
            totalPlayers: 2,
            warnings: [],
            groups: [
              { id: 20, podId: 8, identifier: 'Mesa Beta', playerCount: 2 },
            ],
          },
        ],
        unassignedGroups: [],
      }),
    })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="auto-rebalance-button"]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(assignGroupToPod).toHaveBeenCalledWith(42, 20, 7)
    expect(deletePod).toHaveBeenCalledWith(42, 8)

    wrapper.unmount()
  })
})
