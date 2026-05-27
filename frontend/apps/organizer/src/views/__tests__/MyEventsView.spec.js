import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import MyEventsView from '@/views/MyEventsView.vue'

vi.mock('@/api/events', () => ({
  getMyEvents: vi.fn(),
}))

import { getMyEvents } from '@/api/events'

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/my-events' },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
      { path: '/invitations', name: 'invitations', component: { template: '<div>Invitations</div>' } },
      { path: '/villains', name: 'villains', component: { template: '<div>Villains</div>' } },
      { path: '/inter-pod-cards', name: 'inter-pod-cards', component: { template: '<div>Cards</div>' } },
      { path: '/create-event', name: 'create-event', component: { template: '<div>Create</div>' } },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div>Dashboard</div>' } },
    ],
  })
}

describe('MyEventsView', () => {
  let pinia
  let router

  beforeEach(() => {
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    router = createTestRouter()

    getMyEvents.mockResolvedValue({
      data: [
        {
          id: 8,
          name: 'Evento Compartido',
          status: 'READY',
          configuredPlayerCount: 12,
          timeLimitMinutes: 180,
          eventCode: 'ABC123',
          createdAt: '2026-04-19T10:00:00Z',
          currentUserIsOwner: false,
          currentUserResponsibilities: ['THREAT', 'TIMER'],
        },
      ],
    })
  })

  function mountView() {
    return mount(MyEventsView, {
      global: {
        plugins: [pinia, router],
      },
    })
  }

  it('renders shared events with access badge and responsibilities', async () => {
    const wrapper = mountView()

    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(getMyEvents).toHaveBeenCalled()
    expect(wrapper.find('[data-test="organizer-header-actions"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="organizer-home-link"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Mis Eventos')
    expect(wrapper.text()).toContain('Evento Compartido')
    expect(wrapper.text()).toContain('Co-organizador')
    expect(wrapper.text()).toContain('Amenaza, Temporizador')
  })

  it('navigates to the dashboard when clicking an event card', async () => {
    const wrapper = mountView()
    const pushSpy = vi.spyOn(router, 'push')

    await new Promise((resolve) => setTimeout(resolve, 10))
    await wrapper.find('.event-card').trigger('click')

    expect(pushSpy).toHaveBeenCalledWith({ name: 'dashboard', params: { eventId: 8 } })
  })
})