import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EventSetupView from '@/views/EventSetupView.vue'

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

function buildStructure() {
  return {
    eventId: 42,
    eventName: 'Evento Setup',
    configuredPlayerCount: 8,
    currentGroupCount: 3,
    assignedPlayerCount: 5,
    unassignedGroupCount: 1,
    unassignedPlayerCount: 3,
    schemeThreatTarget: 6,
    suggestedPodName: 'Pod 2',
    pods: [
      {
        id: 7,
        name: 'Pod Norte',
        totalGroups: 2,
        totalPlayers: 5,
        warnings: [],
        groups: [
          { id: 19, podId: 7, identifier: 'Mesa Alfa', playerCount: 2, representativeCode: 'REP1', observerCode: 'OBS1' },
          { id: 21, podId: 7, identifier: 'Mesa Gamma', playerCount: 3, representativeCode: 'REP2', observerCode: 'OBS2' },
        ],
      },
    ],
    unassignedGroups: [
      { id: 20, podId: null, identifier: 'Mesa Beta', playerCount: 3, representativeCode: 'REP3', observerCode: 'OBS3' },
    ],
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
    getEventStructure.mockResolvedValue({ data: buildStructure() })
    createPod.mockResolvedValue({ data: { id: 8, name: 'Pod 2' } })
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
    expect(wrapper.text()).toContain('Nuevo pod')
    expect(wrapper.text()).toContain('Reparto de mesas')
    expect(wrapper.text()).toContain('Sin asignar')
    expect(wrapper.text()).toContain('Mesa Beta')
    expect(wrapper.get('#newPodName').element.value).toBe('Pod 2')
    expect(wrapper.text()).not.toContain('Código Representante')
    expect(wrapper.text()).not.toContain('Código Observador')
    expect(wrapper.find('[data-testid="create-pod-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unassigned-column"]').exists()).toBe(true)

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
})
