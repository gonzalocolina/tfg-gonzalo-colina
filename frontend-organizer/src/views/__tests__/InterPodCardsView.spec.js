import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import InterPodCardsView from '../InterPodCardsView.vue'

const {
  getInterPodCardDefinitions,
  createInterPodCardDefinition,
  updateInterPodCardDefinition,
  deleteInterPodCardDefinition,
  syncImageUrls,
  protectedImagesById,
} = vi.hoisted(() => ({
  getInterPodCardDefinitions: vi.fn(),
  createInterPodCardDefinition: vi.fn(),
  updateInterPodCardDefinition: vi.fn(),
  deleteInterPodCardDefinition: vi.fn(),
  syncImageUrls: vi.fn(),
  protectedImagesById: {},
}))

vi.mock('@/api/interPodCardDefinitions', () => ({
  getInterPodCardDefinitions,
  createInterPodCardDefinition,
  updateInterPodCardDefinition,
  deleteInterPodCardDefinition,
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
  useRoute: () => ({
    params: {},
  }),
}))

const originalCreateObjectURL = URL.createObjectURL
const originalRevokeObjectURL = URL.revokeObjectURL

function buildDefinition() {
  return {
    id: 10,
    name: 'THE_MANGOG',
    type: 'CUSTOM',
    cardCategory: 'ESBIRRO',
    defaultHpMax: 8,
    defaultStatus: 'REVELADA',
    imageUrl: '/api/inter-pod-card-definitions/10/image',
    ownerUsername: 'organizer',
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
})
