import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import MyEventsView from '@/views/MyEventsView.vue'
import CreateEventView from '@/views/CreateEventView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/auth', () => ({
  organizerLogin: vi.fn(),
  coOrganizerLogin: vi.fn(),
  register: vi.fn(),
}))

vi.mock('@/api/events', () => ({
  getMyEvents: vi.fn(),
  createEvent: vi.fn(),
}))

vi.mock('@/api/villains', () => ({
  getVillains: vi.fn(),
}))

vi.mock('@/api/interPodCardDefinitions', () => ({
  getInterPodCardDefinitions: vi.fn(),
}))

import { getMyEvents, createEvent } from '@/api/events'
import { getVillains } from '@/api/villains'
import { getInterPodCardDefinitions } from '@/api/interPodCardDefinitions'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', redirect: '/my-events' },
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
      { path: '/invitations', name: 'invitations', component: { template: '<div>Invitations</div>' } },
      { path: '/villains', name: 'villains', component: { template: '<div>Villains</div>' } },
      {
        path: '/inter-pod-cards',
        name: 'inter-pod-cards',
        component: { template: '<div>Cards</div>' },
      },
      { path: '/create-event', name: 'create-event', component: { template: '<div>Create</div>' } },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div>Dashboard</div>' } },
    ],
  })
}

describe('Organizer basic flows', () => {
  let pinia
  let router

  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()

    pinia = createPinia()
    setActivePinia(pinia)
    router = createTestRouter()

    getMyEvents.mockResolvedValue({
      data: [
        {
          id: 8,
          name: 'Evento Principal',
          status: 'READY',
          configuredPlayerCount: 12,
          timeLimitMinutes: 180,
          eventCode: 'ABC123',
          createdAt: '2026-04-19T10:00:00Z',
          currentUserIsOwner: true,
          currentUserResponsibilities: [],
        },
      ],
    })

    getVillains.mockResolvedValue({
      data: [{ id: 1, name: 'Loki', type: 'PREDEFINED' }],
    })

    getInterPodCardDefinitions.mockResolvedValue({
      data: [
        {
          id: 10,
          name: 'THE_MANGOG',
          cardCategory: 'ESBIRRO',
          defaultHpMax: 8,
          defaultThreatMax: 0,
          defaultStatus: 'REVELADA',
        },
      ],
    })
  })

  function mountView(component) {
    return mount(component, {
      global: {
        plugins: [pinia, router],
      },
    })
  }

  it('testLogin', async () => {
    const wrapper = mountView(LoginView)
    const auth = useAuthStore()
    const pushSpy = vi.spyOn(router, 'push')

    auth.loginOrganizer = vi.fn().mockResolvedValue()

    await wrapper.find('#username').setValue('organizer')
    await wrapper.find('#password').setValue('Password123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.loginOrganizer).toHaveBeenCalledWith('organizer', 'Password123')
    expect(pushSpy).toHaveBeenCalledWith({ name: 'my-events' })
  })

  it('testLoadEvents', async () => {
    const wrapper = mountView(MyEventsView)

    await flushPromises()

    expect(getMyEvents).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Mis Eventos')
    expect(wrapper.text()).toContain('Evento Principal')
    expect(wrapper.text()).toContain('ABC123')
  })

  it('testCreateEvent', async () => {
    createEvent.mockResolvedValue({ data: { id: 99, name: 'Evento Demo' } })
    const wrapper = mountView(CreateEventView)
    const pushSpy = vi.spyOn(router, 'push')

    await flushPromises()
    await wrapper.find('#name').setValue('Evento Demo')
    await wrapper.find('#villain').setValue('1')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(createEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Evento Demo',
        timeLimitMinutes: 180,
        villainDefinitionId: 1,
        interPodCardDefinitionIds: [10],
      }),
    )
    expect(pushSpy).toHaveBeenCalledWith({ name: 'dashboard', params: { eventId: 99 } })
  })
})
