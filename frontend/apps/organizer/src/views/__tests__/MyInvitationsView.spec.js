import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import MyInvitationsView from '@/views/MyInvitationsView.vue'

const { confirmDialog } = vi.hoisted(() => ({
  confirmDialog: vi.fn(),
}))

vi.mock('@/api/invitations', () => ({
  getMyCoOrganizerInvitations: vi.fn(),
  acceptCoOrganizerInvitation: vi.fn(),
  rejectCoOrganizerInvitation: vi.fn(),
}))

vi.mock('@/composables/useDialogs', () => ({
  useDialogs: () => ({
    confirm: confirmDialog,
    alert: vi.fn(),
    currentDialog: { value: null },
    acceptCurrentDialog: vi.fn(),
    dismissCurrentDialog: vi.fn(),
    resetDialogs: vi.fn(),
  }),
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
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
      { path: '/invitations', name: 'invitations', component: { template: '<div>Invitations</div>' } },
      { path: '/create-event', name: 'create-event', component: { template: '<div>Create</div>' } },
      { path: '/villains', name: 'villains', component: { template: '<div>Villains</div>' } },
      { path: '/inter-pod-cards', name: 'inter-pod-cards', component: { template: '<div>InterPod</div>' } },
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
    confirmDialog.mockResolvedValue(true)

    getMyCoOrganizerInvitations.mockResolvedValue({
      data: [
        {
          id: 5,
          eventId: 17,
          eventName: 'Evento Compartido',
          organizerUsername: 'owner-user',
          eventStatus: 'DRAFT',
          timeLimitMinutes: 180,
          responsibilities: ['THREAT', 'TIMER'],
        },
      ],
    })
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
    expect(wrapper.find('[data-test="organizer-header-actions"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="organizer-home-link"]').exists()).toBe(true)
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

    expect(confirmDialog).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Aceptar invitación',
        confirmLabel: 'Aceptar',
      }),
    )
    expect(acceptCoOrganizerInvitation).toHaveBeenCalledWith(5)
    expect(pushSpy).toHaveBeenCalledWith({ name: 'dashboard', params: { eventId: 17 } })
  })

  it('rejects an invitation and refreshes the list', async () => {
    const wrapper = mountView()
    rejectCoOrganizerInvitation.mockResolvedValue({})

    await new Promise((resolve) => setTimeout(resolve, 10))
    const rejectButton = wrapper.findAll('button').find((button) => button.text() === 'Rechazar')
    expect(rejectButton).toBeDefined()
    await rejectButton.trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(confirmDialog).toHaveBeenCalledWith(
      expect.objectContaining({
        title: 'Rechazar invitación',
        confirmLabel: 'Rechazar',
        tone: 'danger',
      }),
    )
    expect(rejectCoOrganizerInvitation).toHaveBeenCalledWith(5)
    expect(getMyCoOrganizerInvitations).toHaveBeenCalledTimes(2)
  })
})