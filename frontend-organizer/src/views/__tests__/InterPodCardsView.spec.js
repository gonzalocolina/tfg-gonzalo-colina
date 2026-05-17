import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import InterPodCardsView from '../InterPodCardsView.vue'

const routeState = vi.hoisted(() => ({
  params: {},
}))

const {
  getInterPodCardDefinitions,
  createInterPodCardDefinition,
  updateInterPodCardDefinition,
  deleteInterPodCardDefinition,
  getEvent,
  updateEvent,
  syncImageUrls,
  protectedImagesById,
} = vi.hoisted(() => ({
  getInterPodCardDefinitions: vi.fn(),
  createInterPodCardDefinition: vi.fn(),
  updateInterPodCardDefinition: vi.fn(),
  deleteInterPodCardDefinition: vi.fn(),
  getEvent: vi.fn(),
  updateEvent: vi.fn(),
  syncImageUrls: vi.fn(),
  protectedImagesById: {},
}))

vi.mock('@/api/interPodCardDefinitions', () => ({
  getInterPodCardDefinitions,
  createInterPodCardDefinition,
  updateInterPodCardDefinition,
  deleteInterPodCardDefinition,
}))

vi.mock('@/api/events', () => ({
  getEvent,
  updateEvent,
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

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

const originalCreateObjectURL = URL.createObjectURL
const originalRevokeObjectURL = URL.revokeObjectURL

function buildDefinition(overrides = {}) {
  return {
    id: 10,
    name: 'THE_MANGOG',
    type: 'CUSTOM',
    cardCategory: 'ESBIRRO',
    defaultHpMax: 8,
    defaultThreatMax: 0,
    defaultStatus: 'REVELADA',
    imageUrl: '/api/inter-pod-card-definitions/10/image',
    ownerUsername: 'organizer',
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
    status: 'DRAFT',
    currentUserIsOwner: true,
    selectedInterPodCardDefinitions: [buildDefinition()],
    ...overrides,
  }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(InterPodCardsView, {
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

describe('InterPodCardsView', () => {
  beforeEach(() => {
    routeState.params = {}
    vi.clearAllMocks()
    Object.keys(protectedImagesById).forEach((key) => delete protectedImagesById[key])
    protectedImagesById['10'] = 'blob:def-10'

    syncImageUrls.mockResolvedValue()
    getInterPodCardDefinitions.mockResolvedValue({
      data: [buildDefinition()],
    })
    createInterPodCardDefinition.mockResolvedValue({ data: { id: 11 } })
    updateInterPodCardDefinition.mockResolvedValue({ data: { id: 10 } })
    deleteInterPodCardDefinition.mockResolvedValue({})
    getEvent.mockResolvedValue({ data: buildEvent() })
    updateEvent.mockResolvedValue({ data: buildEvent() })

    URL.createObjectURL = vi.fn(() => 'blob:replacement-preview')
    URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    URL.createObjectURL = originalCreateObjectURL
    URL.revokeObjectURL = originalRevokeObjectURL
  })

  it('renders catalog images using the protected image helper', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(syncImageUrls).toHaveBeenCalledWith(
      [{ id: 10, url: '/api/inter-pod-card-definitions/10/image' }],
      { force: false },
    )

    const image = wrapper.find('img[alt="Carta"]')
    expect(image.exists()).toBe(true)
    expect(image.attributes('src')).toBe('blob:def-10')
  })

  it('lets the organizer choose another image while editing a custom definition', async () => {
    const wrapper = mountView()
    await flushPromises()

    const editButton = wrapper.findAll('button').find((button) => button.text() === 'Editar')
    expect(editButton).toBeDefined()
    await editButton.trigger('click')

    const replacementImage = new File(['replacement'], 'replacement.png', { type: 'image/png' })
    const fileInput = wrapper.findAll('input[type="file"]')[1]
    Object.defineProperty(fileInput.element, 'files', {
      configurable: true,
      value: [replacementImage],
    })
    await fileInput.trigger('change')

    expect(wrapper.text()).toContain('Nueva imagen: replacement.png')

    const saveButton = wrapper.findAll('button').find((button) => button.text() === 'Guardar')
    expect(saveButton).toBeDefined()
    await saveButton.trigger('click')
    await flushPromises()

    expect(updateInterPodCardDefinition).toHaveBeenCalledWith(
      10,
      expect.objectContaining({
        name: 'THE_MANGOG',
        cardCategory: 'ESBIRRO',
        defaultHpMax: 8,
        defaultStatus: 'REVELADA',
      }),
      replacementImage,
    )
    expect(syncImageUrls).toHaveBeenLastCalledWith(
      [{ id: 10, url: '/api/inter-pod-card-definitions/10/image' }],
      { force: true },
    )
  })

  it('updates the selected cards when the view is opened from an event route', async () => {
    routeState.params = { eventId: '516' }
    const secondDefinition = buildDefinition({
      id: 11,
      name: 'DOOR_BETWEEN_WORLDS',
      imageUrl: null,
    })

    getInterPodCardDefinitions.mockResolvedValue({
      data: [buildDefinition(), secondDefinition],
    })
    updateEvent.mockResolvedValue({
      data: buildEvent({
        selectedInterPodCardDefinitions: [buildDefinition(), secondDefinition],
      }),
    })

    const wrapper = mountView()
    await flushPromises()

    expect(getEvent).toHaveBeenCalledWith('516')
    expect(wrapper.text()).toContain('Selección para el evento')

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect(checkboxes).toHaveLength(2)
    await checkboxes[1].trigger('change')

    const saveButton = wrapper.findAll('button').find((button) => button.text().includes('Guardar cartas del evento'))
    expect(saveButton).toBeDefined()
    await saveButton.trigger('click')
    await flushPromises()

    expect(updateEvent).toHaveBeenCalledWith(
      '516',
      expect.objectContaining({
        name: 'Evento 516',
        timeLimitMinutes: 180,
        villainDefinitionId: 1,
        interPodCardDefinitionIds: [10, 11],
      }),
    )
  })

  it('shows threat values for plan definitions', async () => {
    getInterPodCardDefinitions.mockResolvedValue({
      data: [
        buildDefinition({
          id: 12,
          name: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          defaultHpMax: 0,
          defaultThreatMax: 8,
        }),
      ],
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('Amenaza 8')
  })
})
