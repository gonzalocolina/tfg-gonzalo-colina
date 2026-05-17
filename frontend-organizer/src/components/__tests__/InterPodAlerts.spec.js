import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import InterPodAlerts from '../InterPodAlerts.vue'

const {
  getInterPodCards,
  reportInterPodCardDamage,
  reportInterPodCardThreat,
  updateInterPodCardStatus,
  deleteInterPodCard,
  syncImageUrls,
  protectedImagesById,
} = vi.hoisted(() => ({
  getInterPodCards: vi.fn(),
  reportInterPodCardDamage: vi.fn(),
  reportInterPodCardThreat: vi.fn(),
  updateInterPodCardStatus: vi.fn(),
  deleteInterPodCard: vi.fn(),
  syncImageUrls: vi.fn(),
  protectedImagesById: {},
}))

vi.mock('@/api/interPod', () => ({
  getInterPodCards,
  reportInterPodCardDamage,
  reportInterPodCardThreat,
  updateInterPodCardStatus,
  deleteInterPodCard,
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

function mountComponent() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(InterPodAlerts, {
    props: {
      eventId: 1,
    },
    global: {
      plugins: [pinia],
    },
  })
}

describe('InterPodAlerts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(protectedImagesById).forEach((key) => delete protectedImagesById[key])
    protectedImagesById['99'] = 'blob:card-99'
    syncImageUrls.mockResolvedValue()
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 99,
          podName: 'Pod Norte',
          sourceGroupIdentifier: 'Mesa Beta',
          cardName: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          hpCurrent: 8,
          hpMax: 8,
          status: 'REVELADA',
          imageUrl: '/api/events/1/inter-pod-cards/99/image',
          createdAt: '2026-04-11T12:00:00Z',
        },
      ],
    })
  })

  it('renders active inter-pod card thumbnails through the protected image helper', async () => {
    const wrapper = mountComponent()
    await flushPromises()

    expect(syncImageUrls).toHaveBeenCalledWith([
      { id: 99, url: '/api/events/1/inter-pod-cards/99/image' },
    ])

    const image = wrapper.find('img[alt="Carta inter-nodo"]')
    expect(image.exists()).toBe(true)
    expect(image.attributes('src')).toBe('blob:card-99')
  })

  it('routes plan progress updates to the threat endpoint', async () => {
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 101,
          podName: 'Pod Norte',
          sourceGroupIdentifier: 'Mesa Beta',
          cardName: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          hpCurrent: 0,
          hpMax: 0,
          threatCurrent: 6,
          threatMax: 8,
          status: 'REVELADA',
          createdAt: '2026-04-11T12:00:00Z',
        },
      ],
    })

    const wrapper = mountComponent()
    await flushPromises()

    await wrapper.find('input[type="number"]').setValue('3')
    const threatButton = wrapper.findAll('button').find((button) => button.text() === 'Quitar amenaza')
    expect(threatButton).toBeDefined()
    await threatButton.trigger('click')

    expect(reportInterPodCardThreat).toHaveBeenCalledWith(1, 101, { threat: 3 })
    expect(reportInterPodCardDamage).not.toHaveBeenCalled()
  })
})
