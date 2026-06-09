import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import InterPodAlertDialog from '../InterPodAlertDialog.vue'
import { useGameStateStore } from '@/stores/gameState'
import { seedAuthSession } from '../../../tests/fixtures/authSession.js'
import { createActivePinia } from '../../../tests/utils/createActivePinia.js'

const {
  createInterPodCard,
  reportInterPodCardDamage,
  reportInterPodCardThreat,
  getEventInterPodCardDefinitions,
} = vi.hoisted(() => ({
  createInterPodCard: vi.fn(),
  reportInterPodCardDamage: vi.fn(),
  reportInterPodCardThreat: vi.fn(),
  getEventInterPodCardDefinitions: vi.fn(),
}))

vi.mock('@/api/interPod', () => ({
  createInterPodCard,
  reportInterPodCardDamage,
  reportInterPodCardThreat,
}))

vi.mock('@/api/interPodCardDefinitions', () => ({
  getEventInterPodCardDefinitions,
}))

function buildCard(overrides = {}) {
  return {
    id: 42,
    sourceGroupId: 20,
    interPodCardDefinitionId: 10,
    cardName: 'THE_MANGOG',
    cardCategory: 'ESBIRRO',
    hpCurrent: 7,
    hpMax: 8,
    threatCurrent: 0,
    threatMax: 0,
    status: 'REVELADA',
    sourceGroupIdentifier: 'Mesa Beta',
    createdAt: '2026-03-31T12:00:00Z',
    imageUrl: null,
    ...overrides,
  }
}

function mountDialog(props = {}) {
  const pinia = createActivePinia()

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
    props: {
      podId: 7,
      podName: 'Pod 1',
      initialCardId: null,
      cards: [],
      cardsLoading: false,
      getImageUrl: () => null,
      realtimeConnected: true,
      ...props,
    },
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
    reportInterPodCardDamage.mockReset()
    reportInterPodCardThreat.mockReset()
    getEventInterPodCardDefinitions.mockReset()

    createInterPodCard.mockResolvedValue({ data: { id: 99 } })
    reportInterPodCardDamage.mockResolvedValue({ data: {} })
    reportInterPodCardThreat.mockResolvedValue({ data: {} })
    getEventInterPodCardDefinitions.mockResolvedValue({
      data: [{ id: 10, name: 'THE_MANGOG', cardCategory: 'ESBIRRO', defaultHpMax: 8, defaultThreatMax: 0, defaultStatus: 'REVELADA' }],
    })
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
    sessionStorage.clear()
  })

  it('renders the cards provided by the parent for the current pod', async () => {
    const wrapper = mountDialog({ cards: [buildCard()] })

    await flushPromises()

    expect(wrapper.text()).toContain('Nodo actual: Pod 1')
    expect(wrapper.get('[data-testid="interpod-detail-card-42"]').text()).toContain('The Mangog')

    wrapper.unmount()
  })

  it('locks the background scroll while it is open and restores it when it closes', async () => {
    const appContent = document.createElement('div')
    appContent.className = 'app-content'
    appContent.style.overflow = 'auto'
    appContent.style.touchAction = 'pan-y'
    document.body.appendChild(appContent)

    const scrollToSpy = vi.spyOn(globalThis.window, 'scrollTo').mockImplementation(() => {})
    Object.defineProperty(globalThis.window, 'scrollX', {
      configurable: true,
      value: 18,
    })
    Object.defineProperty(globalThis.window, 'scrollY', {
      configurable: true,
      value: 240,
    })

    const wrapper = mountDialog()

    await flushPromises()

    expect(document.documentElement.style.overflow).toBe('hidden')
    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.style.position).toBe('fixed')
    expect(document.body.style.top).toBe('-240px')
    expect(document.body.style.left).toBe('-18px')
    expect(document.body.style.width).toBe('100%')
    expect(appContent.style.overflow).toBe('hidden')
    expect(appContent.style.touchAction).toBe('none')

    wrapper.unmount()

    expect(document.documentElement.style.overflow).toBe('')
    expect(document.body.style.overflow).toBe('')
    expect(document.body.style.position).toBe('')
    expect(document.body.style.top).toBe('')
    expect(document.body.style.left).toBe('')
    expect(document.body.style.width).toBe('')
    expect(appContent.style.overflow).toBe('auto')
    expect(appContent.style.touchAction).toBe('pan-y')
    expect(scrollToSpy).toHaveBeenCalledWith(18, 240)

    scrollToSpy.mockRestore()
    appContent.remove()
  })

  it('closes when the player taps outside the dialog panel', async () => {
    const wrapper = mountDialog()

    await flushPromises()
    await wrapper.get('.modal-overlay').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)

    wrapper.unmount()
  })

  it('separates active and defeated cards and only renders actions for active ones', async () => {
    const wrapper = mountDialog({
      cards: [
        buildCard(),
        buildCard({
          id: 43,
          cardName: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          hpCurrent: 0,
          hpMax: 0,
          threatCurrent: 0,
          threatMax: 8,
          status: 'DERROTADA',
          sourceGroupIdentifier: 'Mesa Alfa',
        }),
      ],
    })

    await flushPromises()

    const activeSection = wrapper.get('[data-testid="interpod-active-section"]')
    const defeatedSection = wrapper.get('[data-testid="interpod-defeated-section"]')

    expect(activeSection.text()).toContain('Cartas activas del nodo')
    expect(activeSection.text()).toContain('The Mangog')
    expect(activeSection.text()).not.toContain('Door Between Worlds')

    expect(defeatedSection.text()).toContain('Cartas derrotadas del nodo')
    expect(defeatedSection.text()).toContain('Door Between Worlds')
    expect(defeatedSection.text()).toContain('Derrotada')

    expect(activeSection.findAll('input[type="number"]')).toHaveLength(1)
    expect(defeatedSection.findAll('input[type="number"]')).toHaveLength(0)
    expect(defeatedSection.findAll('button').some((button) => ['Registrar daño', 'Quitar amenaza'].includes(button.text()))).toBe(false)

    wrapper.unmount()
  })

  it('creates a card with the minimal payload required without forcing a manual refresh when realtime is connected', async () => {
    const wrapper = mountDialog()

    await flushPromises()
    await wrapper.get('.btn.btn-primary').trigger('click')

    expect(createInterPodCard).toHaveBeenCalledWith(1, {
      representativeCode: 'REP001',
      interPodCardDefinitionId: 10,
    })
    expect(wrapper.emitted('refresh-requested')).toBeUndefined()

    wrapper.unmount()
  })

  it('only offers inter-pod definitions not already reported by the current table', async () => {
    getEventInterPodCardDefinitions.mockResolvedValue({
      data: [
        { id: 10, name: 'THE_MANGOG', cardCategory: 'ESBIRRO', defaultHpMax: 8, defaultThreatMax: 0, defaultStatus: 'REVELADA' },
        { id: 11, name: 'DOOR_BETWEEN_WORLDS', cardCategory: 'PLAN', defaultHpMax: 0, defaultThreatMax: 7, defaultStatus: 'REVELADA' },
      ],
    })
    const wrapper = mountDialog({
      cards: [
        buildCard({
          sourceGroupId: 20,
          interPodCardDefinitionId: 10,
        }),
        buildCard({
          id: 43,
          sourceGroupId: 21,
          interPodCardDefinitionId: 11,
          cardName: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          hpCurrent: 0,
          hpMax: 0,
          threatCurrent: 7,
          threatMax: 7,
          sourceGroupIdentifier: 'Mesa Alfa',
        }),
      ],
    })

    await flushPromises()

    const options = wrapper.findAll('#card-definition option')
    expect(options.map((option) => option.text())).not.toContain('The Mangog')
    expect(options.map((option) => option.text())).toContain('Door Between Worlds')

    await wrapper.get('.btn.btn-primary').trigger('click')

    expect(createInterPodCard).toHaveBeenCalledWith(1, {
      representativeCode: 'REP001',
      interPodCardDefinitionId: 11,
    })

    wrapper.unmount()
  })

  it('requests a fallback refresh after creating a card when realtime is disconnected', async () => {
    const wrapper = mountDialog({ realtimeConnected: false })

    await flushPromises()
    await wrapper.get('.btn.btn-primary').trigger('click')

    expect(wrapper.emitted('refresh-requested')).toHaveLength(1)

    wrapper.unmount()
  })

  it('renders provided image thumbnails and opens the in-app viewer', async () => {
    const getImageUrl = vi.fn((cardId) => (cardId === 42 ? 'blob:dialog-card-42' : null))
    const wrapper = mountDialog({
      cards: [buildCard({ imageUrl: '/api/events/1/inter-pod-cards/42/image' })],
      getImageUrl,
    })

    await flushPromises()

    expect(wrapper.find('.interpod-card-thumb').attributes('src')).toBe('blob:dialog-card-42')
    expect(wrapper.find('[target="_blank"]').exists()).toBe(false)

    await wrapper.get('.interpod-card-thumb-button').trigger('click')

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(true)
    expect(wrapper.find('.image-preview-img').attributes('src')).toBe('blob:dialog-card-42')

    await wrapper.get('.image-preview-close').trigger('click')

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(false)

    wrapper.unmount()
  })

  it('closes the fullscreen image when the browser back event is triggered', async () => {
    const wrapper = mountDialog({
      cards: [buildCard({ imageUrl: '/api/events/1/inter-pod-cards/42/image' })],
      getImageUrl: (cardId) => (cardId === 42 ? 'blob:dialog-card-42' : null),
    })

    await flushPromises()
    await wrapper.get('.interpod-card-thumb-button').trigger('click')

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(true)

    globalThis.dispatchEvent(new PopStateEvent('popstate'))
    await flushPromises()

    expect(wrapper.find('.image-preview-overlay').exists()).toBe(false)
    expect(wrapper.emitted('close')).toBeUndefined()

    wrapper.unmount()
  })

  it('highlights the requested card when opened from the summary view', async () => {
    const wrapper = mountDialog({
      initialCardId: 42,
      cards: [
        buildCard({
          id: 41,
          cardName: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          hpCurrent: 0,
          hpMax: 0,
          threatCurrent: 8,
          threatMax: 8,
          sourceGroupIdentifier: 'Mesa Alfa',
        }),
        buildCard(),
      ],
    })

    await flushPromises()

    const highlightedCard = wrapper.find('.interpod-card-item--highlighted')
    expect(highlightedCard.exists()).toBe(true)
    expect(highlightedCard.text()).toContain('The Mangog')

    wrapper.unmount()
  })

  it('does not render manual status controls for player cards', async () => {
    const wrapper = mountDialog({ cards: [buildCard()] })

    await flushPromises()

    expect(wrapper.findAll('select').length).toBe(1)
    expect(wrapper.text()).not.toContain('Estado actualizado')
    expect(wrapper.findAll('button').some((button) => button.text() === 'Estado')).toBe(false)

    wrapper.unmount()
  })

  it('uses the threat endpoint for plan cards', async () => {
    const wrapper = mountDialog({
      cards: [
        buildCard({
          id: 52,
          cardName: 'DOOR_BETWEEN_WORLDS',
          cardCategory: 'PLAN',
          hpCurrent: 0,
          hpMax: 0,
          threatCurrent: 5,
          threatMax: 8,
        }),
      ],
    })

    await flushPromises()

    await wrapper.find('input[type="number"]').setValue('2')
    const threatButton = wrapper.findAll('button').find((button) => button.text() === 'Quitar amenaza')
    expect(threatButton).toBeDefined()
    await threatButton.trigger('click')

    expect(reportInterPodCardThreat).toHaveBeenCalledWith(1, 52, {
      representativeCode: 'REP001',
      threat: 2,
    })
    expect(reportInterPodCardDamage).not.toHaveBeenCalled()
    expect(wrapper.emitted('refresh-requested')).toBeUndefined()

    wrapper.unmount()
  })

  it('requests a fallback refresh after mutating a card when realtime is disconnected', async () => {
    const wrapper = mountDialog({
      realtimeConnected: false,
      cards: [buildCard()],
    })

    await flushPromises()

    await wrapper.find('input[type="number"]').setValue('2')
    const damageButton = wrapper.findAll('button').find((button) => button.text() === 'Registrar daño')
    expect(damageButton).toBeDefined()
    await damageButton.trigger('click')

    expect(reportInterPodCardDamage).toHaveBeenCalledWith(1, 42, {
      representativeCode: 'REP001',
      damage: 2,
    })
    expect(wrapper.emitted('refresh-requested')).toHaveLength(1)

    wrapper.unmount()
  })

  it('disables inter-pod actions while the game is paused', async () => {
    const wrapper = mountDialog({ cards: [buildCard()] })
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
    const wrapper = mountDialog({ cards: [buildCard()] })
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
