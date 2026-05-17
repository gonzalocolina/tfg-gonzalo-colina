import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import MyInvitationsView from '@/views/MyInvitationsView.vue'

vi.mock('@/api/invitations', () => ({
  getMyCoOrganizerInvitations: vi.fn(),
  acceptCoOrganizerInvitation: vi.fn(),
  rejectCoOrganizerInvitation: vi.fn(),
}))

import {
  acceptCoOrganizerInvitation,
  getMyCoOrganizerInvitations,
  rejectCoOrganizerInvitation,
} from '@/api/invitations'

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/invitations' },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
      { path: '/invitations', name: 'invitations', component: { template: '<div>Invitations</div>' } },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div>Dashboard</div>' } },
    ],
  })
}

describe('MyInvitationsView', () => {
  let pinia
  let router

  beforeEach(() => {
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    router = createTestRouter()
    vi.stubGlobal('confirm', vi.fn(() => true))

    getMyCoOrganizerInvitations.mockResolvedValue({
      data: [
        {
          id: 5,
          eventId: 17,
          eventName: 'Evento Compartido',
          organizerUsername: 'owner-user',
          eventStatus: 'READY',
          timeLimitMinutes: 180,
          responsibilities: ['THREAT', 'TIMER'],
        },
      ],
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function mountView() {
    return mount(MyInvitationsView, {
      global: {
        plugins: [pinia, router],
      },
    })
  }

  it('renders pending invitations with event summary', async () => {
    const wrapper = mountView()

    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(getMyCoOrganizerInvitations).toHaveBeenCalled()
    expect(wrapper.text()).toContain('Evento Compartido')
    expect(wrapper.text()).toContain('owner-user')
    expect(wrapper.text()).toContain('Amenaza, Temporizador')
  })

  it('accepts an invitation and redirects to the dashboard', async () => {
    const wrapper = mountView()
    const pushSpy = vi.spyOn(router, 'push')
    acceptCoOrganizerInvitation.mockResolvedValue({ data: { eventId: 17 } })

    await new Promise((resolve) => setTimeout(resolve, 10))
    await wrapper.find('button.btn-primary').trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(acceptCoOrganizerInvitation).toHaveBeenCalledWith(5)
    expect(pushSpy).toHaveBeenCalledWith({ name: 'dashboard', params: { eventId: 17 } })
  })

  it('rejects an invitation and refreshes the list', async () => {
    const wrapper = mountView()
    rejectCoOrganizerInvitation.mockResolvedValue({})

    await new Promise((resolve) => setTimeout(resolve, 10))
    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(rejectCoOrganizerInvitation).toHaveBeenCalledWith(5)
    expect(getMyCoOrganizerInvitations).toHaveBeenCalledTimes(2)
  })
})