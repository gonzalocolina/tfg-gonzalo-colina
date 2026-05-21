import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EventStatisticsView from '@/views/EventStatisticsView.vue'

const { replace } = vi.hoisted(() => ({
  replace: vi.fn(),
}))

const { warning, error } = vi.hoisted(() => ({
  warning: vi.fn(),
  error: vi.fn(),
}))

const { getGameState } = vi.hoisted(() => ({
  getGameState: vi.fn(),
}))

const { getEventLogs } = vi.hoisted(() => ({
  getEventLogs: vi.fn(),
}))

const { getEventStructure } = vi.hoisted(() => ({
  getEventStructure: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { eventId: '42' },
  }),
  useRouter: () => ({
    replace,
  }),
}))

vi.mock('@/stores/notifications', () => ({
  useNotificationsStore: () => ({ warning, error }),
}))

vi.mock('@/api/gameState', () => ({
  getGameState,
}))

vi.mock('@/api/logs', () => ({
  getEventLogs,
}))

vi.mock('@/api/setup', () => ({
  getEventStructure,
}))

function buildSnapshot(status = 'FINISHED') {
  return {
    eventId: 42,
    eventStatus: status,
    eventResult: status === 'FINISHED' ? 'PLAYERS_VICTORY' : null,
    villainName: 'Loki',
    villainType: 'TRICKSTER',
    villainHpCurrent: 0,
    villainHpMax: 40,
    schemeThreatCurrent: 2,
    schemeThreatTarget: 12,
    timerRemainingSeconds: 0,
    timerStatus: 'FINISHED',
    totalReportedVillainDamage: 40,
    startedAt: '2026-05-18T10:00:00Z',
    finishedAt: status === 'FINISHED' ? '2026-05-18T11:10:00Z' : null,
    updatedAt: '2026-05-18T11:10:00Z',
    finalSummary: status === 'FINISHED'
      ? {
          result: 'PLAYERS_VICTORY',
          cause: 'VILLAIN_HP_ZERO',
          durationSeconds: 4200,
          totalReportedVillainDamage: 40,
          finalThreat: 2,
        }
      : null,
  }
}

function buildStructure() {
  return {
    eventId: 42,
    eventName: 'Evento Loki',
    configuredPlayerCount: 8,
    pods: [
      {
        id: 1,
        name: 'Pod Norte',
        groups: [
          {
            id: 11,
            identifier: 'Mesa Alfa',
            playerCount: 4,
          },
        ],
      },
    ],
    unassignedGroups: [],
  }
}

function buildLogs() {
  return [
    {
      id: 1,
      timestamp: '2026-05-18T10:01:00Z',
      actor: 'rep-a',
      groupIdentifier: 'Mesa Alfa',
      actionType: 'VILLAIN_DAMAGE_REPORTED',
      previousValue: '40',
      newValue: '30',
      notes: 'Daño reportado al villano por la mesa.',
    },
    {
      id: 2,
      timestamp: '2026-05-18T10:15:00Z',
      actor: 'coorg',
      groupIdentifier: null,
      actionType: 'TIMER_PAUSED',
      previousValue: null,
      newValue: '1800',
      notes: 'Temporizador pausado.',
    },
    {
      id: 3,
      timestamp: '2026-05-18T11:10:00Z',
      actor: null,
      groupIdentifier: null,
      actionType: 'EVENT_FINISHED',
      previousValue: null,
      newValue: 'PLAYERS_VICTORY',
      notes: 'Partida finalizada por VILLAIN_HP_ZERO',
    },
  ]
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  return mount(EventStatisticsView, {
    global: {
      plugins: [pinia],
      stubs: {
        FinalSummary: { template: '<div class="final-summary-stub" />' },
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('EventStatisticsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    replace.mockResolvedValue(undefined)
    getGameState.mockResolvedValue({ data: buildSnapshot('FINISHED') })
    getEventLogs.mockResolvedValue({ data: buildLogs() })
    getEventStructure.mockResolvedValue({ data: buildStructure() })
  })

  it('loads and renders statistics for a finished event', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(getGameState).toHaveBeenCalledWith('42')
    expect(getEventLogs).toHaveBeenCalledWith('42')
    expect(getEventStructure).toHaveBeenCalledWith('42')
    expect(wrapper.text()).toContain('Estadísticas de la partida')
    expect(wrapper.text()).toContain('Evento Loki')
    expect(wrapper.text()).toContain('Mesa Alfa')
    expect(wrapper.text()).toContain('Ejes independientes')
    expect(wrapper.find('[data-testid="statistics-groups-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="statistics-trend-chart"]').exists()).toBe(true)
    expect(wrapper.find('.final-summary-stub').exists()).toBe(true)

    wrapper.unmount()
  })

  it('redirects to the game view when the event is not finished', async () => {
    getGameState.mockResolvedValueOnce({ data: buildSnapshot('IN_PROGRESS') })

    const wrapper = mountView()
    await flushPromises()

    expect(warning).toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith({
      name: 'game',
      params: { eventId: '42' },
    })
    expect(getEventLogs).not.toHaveBeenCalled()
    expect(getEventStructure).not.toHaveBeenCalled()

    wrapper.unmount()
  })
})
