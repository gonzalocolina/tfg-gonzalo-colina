import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import VillainsView from '../VillainsView.vue'

const routeState = vi.hoisted(() => ({
  params: {},
}))

const {
  getVillains,
  createVillain,
  updateVillainCatalog,
  deleteVillain,
  getEvent,
  updateEvent,
} = vi.hoisted(() => ({
  getVillains: vi.fn(),
  createVillain: vi.fn(),
  updateVillainCatalog: vi.fn(),
  deleteVillain: vi.fn(),
  getEvent: vi.fn(),
  updateEvent: vi.fn(),
}))

vi.mock('@/api/villains', () => ({
  getVillains,
  createVillain,
  updateVillain: updateVillainCatalog,
  deleteVillain,
}))

vi.mock('@/api/events', () => ({
  getEvent,
  updateEvent,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

function buildVillain(overrides = {}) {
  return {
    id: 1,
    name: 'Loki',
    type: 'PREDEFINED',
    hpRuleType: 'MULTIPLIER',
    hpRuleValue: 20,
    threatRuleType: 'MULTIPLIER',
    threatRuleValue: 2,
    ownerUsername: null,
    ...overrides,
  }
}

function buildEvent(overrides = {}) {
  return {
    id: 516,
    name: 'Evento 516',
    timeLimitMinutes: 180,
    villainDefinitionId: 1,
    villainName: 'Loki',
    villainType: 'PREDEFINED',
    status: 'DRAFT',
    currentUserIsOwner: true,
    selectedInterPodCardDefinitions: [{ id: 10, name: 'THE_MANGOG' }],
    ...overrides,
  }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(VillainsView, {
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: {
          template: '<a><slot /></a>',
        },
      },
    },
  })
}

describe('VillainsView', () => {
  beforeEach(() => {
    routeState.params = {}
    vi.clearAllMocks()

    getVillains.mockResolvedValue({
      data: [
        buildVillain(),
        buildVillain({
          id: 2,
          name: 'Hela',
          type: 'CUSTOM',
          ownerUsername: 'organizer',
        }),
      ],
    })
    createVillain.mockResolvedValue({ data: { id: 3 } })
    updateVillainCatalog.mockResolvedValue({ data: { id: 2 } })
    deleteVillain.mockResolvedValue({})
    getEvent.mockResolvedValue({ data: buildEvent() })
    updateEvent.mockResolvedValue({
      data: buildEvent({
        villainDefinitionId: 2,
        villainName: 'Hela',
        villainType: 'CUSTOM',
      }),
    })
  })

  it('renders the global catalog mode by default', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getEvent).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Gestión de Villanos')
    expect(wrapper.text()).not.toContain('Selección para el evento')
  })

  it('updates the selected villain when opened from an event route', async () => {
    routeState.params = { eventId: '516' }

    const wrapper = mountView()
    await flushPromises()

    expect(getEvent).toHaveBeenCalledWith('516')
    expect(wrapper.text()).toContain('Villanos del Evento')

    const radios = wrapper.findAll('input[type="radio"]')
    expect(radios).toHaveLength(2)
    await radios[1].trigger('change')

    const saveButton = wrapper.findAll('button').find((button) => button.text().includes('Guardar villano del evento'))
    expect(saveButton).toBeDefined()
    await saveButton.trigger('click')
    await flushPromises()

    expect(updateEvent).toHaveBeenCalledWith(
      '516',
      expect.objectContaining({
        name: 'Evento 516',
        timeLimitMinutes: 180,
        villainDefinitionId: 2,
        interPodCardDefinitionIds: [10],
      }),
    )
    expect(wrapper.text()).toContain('Hela')
  })
})