import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import InterPodAlertDialog from '../InterPodAlertDialog.vue'
import { useGameStateStore } from '@/stores/gameState'

const {
  createInterPodCard,
  getInterPodCards,
  reportInterPodCardDamage,
  getEventInterPodCardDefinitions,
  syncImageUrls,
  protectedImagesById,
} = vi.hoisted(() => ({
  createInterPodCard: vi.fn(),
  getInterPodCards: vi.fn(),
  reportInterPodCardDamage: vi.fn(),
  getEventInterPodCardDefinitions: vi.fn(),
  syncImageUrls: vi.fn(),
  protectedImagesById: {},
}))

vi.mock('@/api/interPod', () => ({
  createInterPodCard,
  getInterPodCards,
  reportInterPodCardDamage,
}))

vi.mock('@/api/interPodCardDefinitions', () => ({
  getEventInterPodCardDefinitions,
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

function seedAuthSession() {
  sessionStorage.setItem(
    'auth-player',
    JSON.stringify({
      accessToken: 'tok-123',
      role: 'GROUP_REPRESENTATIVE',
      eventId: 1,
      groupId: 20,
      displayName: 'Mesa Beta',
      code: 'REP001',
    }),
  )
}

function mountDialog(props = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const gs = useGameStateStore()
  gs.applySnapshot({
    eventId: 1,
    eventStatus: 'IN_PROGRESS',
    eventResult: null,
    villainName: 'Loki',
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
  })

  return mount(InterPodAlertDialog, {
    props,
    global: {
      plugins: [pinia],
    },
  })
}

describe('InterPodAlertDialog', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    seedAuthSession()
    createInterPodCard.mockReset()
    getInterPodCards.mockReset()
    reportInterPodCardDamage.mockReset()
    getEventInterPodCardDefinitions.mockReset()
    syncImageUrls.mockReset()
    Object.keys(protectedImagesById).forEach((key) => delete protectedImagesById[key])
    getInterPodCards.mockResolvedValue({ data: [] })
    getEventInterPodCardDefinitions.mockResolvedValue({
      data: [{ id: 10, name: 'THE_MANGOG', defaultHpMax: 8, defaultStatus: 'REVELADA' }],
    })
    syncImageUrls.mockResolvedValue()
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
    sessionStorage.clear()
  })

  it('loads cards using the current pod id when it becomes available', async () => {
    const wrapper = mountDialog({ podId: null, podName: '' })

    await flushPromises()
    expect(getInterPodCards).not.toHaveBeenCalled()

    await wrapper.setProps({ podId: 7, podName: 'Pod Norte' })
    await flushPromises()

    expect(getInterPodCards).toHaveBeenCalledWith(1, 7)
    expect(wrapper.text()).toContain('Pod actual: Pod Norte')

    wrapper.unmount()
  })

  it('creates a card with the minimal payload required by the player flow', async () => {
    createInterPodCard.mockResolvedValue({ data: { id: 99 } })

    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    await flushPromises()

    await wrapper.get('.btn.btn-primary').trigger('click')

    expect(createInterPodCard).toHaveBeenCalledWith(1, {
      representativeCode: 'REP001',
      interPodCardDefinitionId: 10,
    })

    wrapper.unmount()
  })

  it('renders protected image thumbnails for active inter-pod cards', async () => {
    protectedImagesById['42'] = 'blob:dialog-card-42'
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 42,
          cardName: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          hpCurrent: 7,
          hpMax: 8,
          status: 'REVELADA',
          sourceGroupIdentifier: 'Mesa Beta',
          createdAt: '2026-03-31T12:00:00Z',
          imageUrl: '/api/events/1/inter-pod-cards/42/image',
        },
      ],
    })

    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    await flushPromises()

    expect(syncImageUrls).toHaveBeenCalledWith([
      { id: 42, url: '/api/events/1/inter-pod-cards/42/image' },
    ])
    expect(wrapper.find('.interpod-card-thumb').attributes('src')).toBe('blob:dialog-card-42')
    expect(wrapper.find('.interpod-card-thumb-button').exists()).toBe(true)
    expect(wrapper.find('[target="_blank"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('opens the image in an in-app viewer and allows closing it', async () => {
    protectedImagesById['42'] = 'blob:dialog-card-42'
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 42,
          cardName: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          hpCurrent: 7,
          hpMax: 8,
          status: 'REVELADA',
          sourceGroupIdentifier: 'Mesa Beta',
          createdAt: '2026-03-31T12:00:00Z',
          imageUrl: '/api/events/1/inter-pod-cards/42/image',
        },
      ],
    })

    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    await flushPromises()

    await wrapper.get('.interpod-card-thumb-button').trigger('click')

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(true)
    expect(wrapper.find('.image-preview-img').attributes('src')).toBe('blob:dialog-card-42')

    await wrapper.get('.image-preview-close').trigger('click')

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(false)

    wrapper.unmount()
  })

  it('closes the fullscreen image when the browser back event is triggered', async () => {
    protectedImagesById['42'] = 'blob:dialog-card-42'
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 42,
          cardName: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          hpCurrent: 7,
          hpMax: 8,
          status: 'REVELADA',
          sourceGroupIdentifier: 'Mesa Beta',
          createdAt: '2026-03-31T12:00:00Z',
          imageUrl: '/api/events/1/inter-pod-cards/42/image',
        },
      ],
    })

    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    await flushPromises()

    await wrapper.get('.interpod-card-thumb-button').trigger('click')
    expect(wrapper.find('.image-preview-overlay').exists()).toBe(true)

    window.dispatchEvent(new PopStateEvent('popstate'))
    await flushPromises()

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(false)
    expect(wrapper.emitted('close')).toBeUndefined()

    wrapper.unmount()
  })

  it('highlights the requested card when opened from the summary view', async () => {
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 41,
          cardName: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          hpCurrent: 8,
          hpMax: 8,
          status: 'REVELADA',
          sourceGroupIdentifier: 'Mesa Alfa',
          createdAt: '2026-03-31T12:00:00Z',
          imageUrl: null,
        },
        {
          id: 42,
          cardName: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          hpCurrent: 7,
          hpMax: 8,
          status: 'REVELADA',
          sourceGroupIdentifier: 'Mesa Beta',
          createdAt: '2026-03-31T12:00:00Z',
          imageUrl: null,
        },
      ],
    })

    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte', initialCardId: 42 })
    await flushPromises()

    const highlightedCard = wrapper.find('.interpod-card-item--highlighted')
    expect(highlightedCard.exists()).toBe(true)
    expect(highlightedCard.text()).toContain('The Mangog')

    wrapper.unmount()
  })

  it('does not render manual status controls for player cards', async () => {
    getInterPodCards.mockResolvedValue({
      data: [
        {
          id: 42,
          cardName: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          hpCurrent: 7,
          hpMax: 8,
          status: 'REVELADA',
          sourceGroupIdentifier: 'Mesa Beta',
          createdAt: '2026-03-31T12:00:00Z',
          imageUrl: null,
        },
      ],
    })

    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    await flushPromises()

    expect(wrapper.findAll('select').length).toBe(1)
    expect(wrapper.text()).not.toContain('Estado actualizado')
    expect(wrapper.findAll('button').some((button) => button.text() === 'Estado')).toBe(false)

    wrapper.unmount()
  })

  it('disables inter-pod actions while the game is paused', async () => {
    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    const gs = useGameStateStore()
    gs.applySnapshot({
      eventId: 1,
      eventStatus: 'IN_PROGRESS',
      eventResult: null,
      villainName: 'Loki',
      villainHpCurrent: 40,
      villainHpMax: 60,
      schemeThreatCurrent: 3,
      schemeThreatTarget: 6,
      timerRemainingSeconds: 7200,
      timerStatus: 'PAUSED',
      totalReportedVillainDamage: 20,
      startedAt: null,
      finishedAt: null,
      updatedAt: '2026-03-31T12:00:00Z',
      finalSummary: null,
    })

    await flushPromises()

    expect(wrapper.text()).toContain('La partida está pausada')
    expect(wrapper.get('.btn.btn-primary').attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('disables inter-pod actions while the timer has not started yet', async () => {
    const wrapper = mountDialog({ podId: 7, podName: 'Pod Norte' })
    const gs = useGameStateStore()
    gs.applySnapshot({
      eventId: 1,
      eventStatus: 'IN_PROGRESS',
      eventResult: null,
      villainName: 'Loki',
      villainHpCurrent: 40,
      villainHpMax: 60,
      schemeThreatCurrent: 3,
      schemeThreatTarget: 6,
      timerRemainingSeconds: 7200,
      timerStatus: 'READY',
      totalReportedVillainDamage: 20,
      startedAt: null,
      finishedAt: null,
      updatedAt: '2026-03-31T12:00:00Z',
      finalSummary: null,
    })

    await flushPromises()

    expect(wrapper.text()).toContain('todavía no ha iniciado el contador')
    expect(wrapper.get('.btn.btn-primary').attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })
})
