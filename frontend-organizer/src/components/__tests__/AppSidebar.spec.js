import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import AppSidebar from '../AppSidebar.vue'
import { useAuthStore } from '@/stores/auth'

const { getGameState } = vi.hoisted(() => ({
  getGameState: vi.fn(),
}))

const { getEvent } = vi.hoisted(() => ({
  getEvent: vi.fn(),
}))

vi.mock('@/api/gameState', () => ({
  getGameState,
}))

vi.mock('@/api/events', () => ({
  getEvent,
}))

function buildSnapshot(status = 'DRAFT', overrides = {}) {
  return {
    eventId: 42,
    eventStatus: status,
    eventResult: null,
    villainName: 'Loki',
    villainType: 'TRICKSTER',
    villainHpCurrent: 30,
    villainHpMax: 40,
    schemeThreatCurrent: 2,
    schemeThreatTarget: 6,
    timerRemainingSeconds: 0,
    timerStatus: 'NOT_CONFIGURED',
    totalReportedVillainDamage: 0,
    startedAt: null,
    finishedAt: null,
    updatedAt: '2026-04-11T18:00:00Z',
    finalSummary: status === 'FINISHED'
      ? {
          result: 'PLAYERS_VICTORY',
          cause: 'VILLAIN_HP_ZERO',
          durationSeconds: 3600,
          totalReportedVillainDamage: 42,
          finalThreat: 3,
        }
      : null,
    ...overrides,
  }
}

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My events</div>' } },
      { path: '/invitations', name: 'invitations', component: { template: '<div>Invitations</div>' } },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div>Dashboard</div>' } },
      { path: '/event/:eventId/setup', name: 'setup', component: { template: '<div>Setup</div>' } },
      { path: '/event/:eventId/villains', name: 'event-villains', component: { template: '<div>Event villains</div>' } },
      {
        path: '/event/:eventId/inter-pod-cards',
        name: 'event-inter-pod-cards',
        component: { template: '<div>Event inter-pod cards</div>' },
      },
      { path: '/event/:eventId/roles', name: 'roles', component: { template: '<div>Roles</div>' } },
      { path: '/event/:eventId/game', name: 'game', component: { template: '<div>Game</div>' } },
      { path: '/event/:eventId/statistics', name: 'statistics', component: { template: '<div>Statistics</div>' } },
      { path: '/event/:eventId/logs', name: 'logs', component: { template: '<div>Logs</div>' } },
      { path: '/villains', name: 'villains', component: { template: '<div>Villains</div>' } },
      { path: '/inter-pod-cards', name: 'inter-pod-cards', component: { template: '<div>InterPod</div>' } },
    ],
  })
}

async function mountSidebar(initialPath) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const auth = useAuthStore()
  auth.setSession({
    accessToken: 'tok-123',
    role: 'ORGANIZER',
    eventId: null,
    groupId: null,
    username: 'organizer',
    displayName: 'Organizador',
  })

  const router = createTestRouter()
  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(AppSidebar, {
    global: {
      plugins: [pinia, router],
    },
  })

  await flushPromises()
  return { wrapper }
}

describe('AppSidebar', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
    getGameState.mockResolvedValue({ data: buildSnapshot() })
    getEvent.mockResolvedValue({
      data: {
        id: 42,
        currentUserIsOwner: true,
      },
    })
  })

  it('shows invitations only on the my-events home route', async () => {
    const { wrapper } = await mountSidebar('/my-events')

    expect(getGameState).not.toHaveBeenCalled()
    expect(getEvent).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Mis invitaciones')
    expect(wrapper.text()).toContain('Villanos')
    expect(wrapper.text()).toContain('Cartas Inter-Nodo')
    expect(wrapper.find('a[href="/invitations"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/villains"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/inter-pod-cards"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('keeps the event navigation visible on the event villains route', async () => {
    const { wrapper } = await mountSidebar('/event/42/villains')

    expect(getGameState).toHaveBeenCalledWith('42')
    expect(getEvent).toHaveBeenCalledWith('42')
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).toContain('Nodos y mesas')
    expect(wrapper.text()).toContain('Co-organizadores')
    expect(wrapper.text()).not.toContain('Mis invitaciones')
    expect(wrapper.text()).toContain('Partida')
    expect(wrapper.text()).toContain('Registro (Log)')
    expect(wrapper.find('a[href="/event/42/roles"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/event/42/villains"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/event/42/inter-pod-cards"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/invitations"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('keeps the event navigation visible for non-owners on the event villains route', async () => {
    getEvent.mockResolvedValueOnce({
      data: {
        id: 42,
        currentUserIsOwner: false,
      },
    })

    const { wrapper } = await mountSidebar('/event/42/villains')

    expect(getGameState).toHaveBeenCalledWith('42')
    expect(getEvent).toHaveBeenCalledWith('42')
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).not.toContain('Mis invitaciones')
    expect(wrapper.text()).not.toContain('Nodos y mesas')
    expect(wrapper.text()).not.toContain('Co-organizadores')
    expect(wrapper.text()).toContain('Partida')
    expect(wrapper.text()).toContain('Registro (Log)')
    expect(wrapper.find('a[href="/event/42/villains"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/event/42/inter-pod-cards"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/invitations"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/villains"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/inter-pod-cards"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('keeps the event navigation visible for non-owners on the event inter-pod cards route', async () => {
    getEvent.mockResolvedValueOnce({
      data: {
        id: 42,
        currentUserIsOwner: false,
      },
    })

    const { wrapper } = await mountSidebar('/event/42/inter-pod-cards')

    expect(getGameState).toHaveBeenCalledWith('42')
    expect(getEvent).toHaveBeenCalledWith('42')
    expect(wrapper.text()).toContain('Dashboard')
    expect(wrapper.text()).not.toContain('Mis invitaciones')
    expect(wrapper.text()).not.toContain('Nodos y mesas')
    expect(wrapper.text()).not.toContain('Co-organizadores')
    expect(wrapper.text()).toContain('Partida')
    expect(wrapper.text()).toContain('Registro (Log)')
    expect(wrapper.find('a[href="/event/42/villains"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/event/42/inter-pod-cards"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/invitations"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/villains"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/inter-pod-cards"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('shows only the global catalog options outside event context', async () => {
    const { wrapper } = await mountSidebar('/villains')

    expect(getGameState).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('Dashboard')
    expect(wrapper.text()).not.toContain('Mis invitaciones')
    expect(wrapper.text()).not.toContain('Nodos y Mesas')
    expect(wrapper.text()).not.toContain('Co-organizadores')
    expect(wrapper.text()).toContain('Villanos')
    expect(wrapper.text()).toContain('Cartas Inter-Nodo')
    expect(wrapper.find('a[href="/invitations"]').exists()).toBe(false)
    expect(wrapper.find('a[href="/villains"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/inter-pod-cards"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('shows the statistics link only when the event is finished', async () => {
    getGameState.mockResolvedValueOnce({ data: buildSnapshot('FINISHED') })

    const { wrapper } = await mountSidebar('/event/42/game')

    expect(wrapper.text()).toContain('Estadísticas')
    expect(wrapper.find('a[href="/event/42/statistics"]').exists()).toBe(true)

    wrapper.unmount()
  })
})
