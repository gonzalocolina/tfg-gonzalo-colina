import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import CreateEventView from '@/views/CreateEventView.vue'

// Mock APIs
vi.mock('@/api/events', () => ({
  createEvent: vi.fn(),
}))
vi.mock('@/api/villains', () => ({
  getVillains: vi.fn(),
}))
vi.mock('@/api/interPodCardDefinitions', () => ({
  getInterPodCardDefinitions: vi.fn(),
}))
vi.mock('@/api/auth', () => ({
  organizerLogin: vi.fn(),
  coOrganizerLogin: vi.fn(),
  register: vi.fn(),
}))

import { createEvent } from '@/api/events'
import { getVillains } from '@/api/villains'
import { getInterPodCardDefinitions } from '@/api/interPodCardDefinitions'

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/my-events' },
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
      { path: '/create-event', name: 'create-event', component: { template: '<div>Create</div>' } },
      { path: '/villains', name: 'villains', component: { template: '<div>Villains</div>' } },
      {
        path: '/inter-pod-cards',
        name: 'inter-pod-cards',
        component: { template: '<div>InterPodCards</div>' },
      },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div>Dashboard</div>' } },
    ],
  })
}

describe('CreateEventView', () => {
  let pinia
  let router

  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
    getVillains.mockResolvedValue({
      data: [{ id: 1, name: 'Loki', type: 'PREDEFINED' }],
    })
    getInterPodCardDefinitions.mockResolvedValue({
      data: [
        { id: 10, name: 'THE_MANGOG', cardCategory: 'ESBIRRO', defaultHpMax: 8, defaultStatus: 'REVELADA' },
      ],
    })
    pinia = createPinia()
    setActivePinia(pinia)
    router = createTestRouter()
  })

  function mountView() {
    return mount(CreateEventView, {
      global: { plugins: [pinia, router] },
    })
  }

  it('renders the create event form', async () => {
    const wrapper = mountView()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(wrapper.find('h1').text()).toBe('Crear Evento')
    expect(wrapper.find('#name').exists()).toBe(true)
    expect(wrapper.find('#players').exists()).toBe(false)
    expect(wrapper.find('#villain').exists()).toBe(true)
    expect(wrapper.text()).toContain('Cartas inter-nodo para este evento')
    expect(wrapper.text()).toContain('0 = sin temporizador')
    // Organizer fields no longer exist
    expect(wrapper.find('#orgUser').exists()).toBe(false)
    expect(wrapper.find('#orgPass').exists()).toBe(false)
  })

  it('uses eventId from createEvent response for redirect', async () => {
    const wrapper = mountView()
    const pushSpy = vi.spyOn(router, 'push')

    createEvent.mockResolvedValue({ data: { id: 99, name: 'Test Event' } })

    await new Promise((r) => setTimeout(r, 0))
    await wrapper.find('#name').setValue('Test Event')
    await wrapper.find('#villain').setValue('1')
    await wrapper.find('form').trigger('submit')

    await new Promise((r) => setTimeout(r, 50))

    const payload = createEvent.mock.calls[0][0]

    expect(createEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Test Event',
        timeLimitMinutes: 180,
        villainDefinitionId: 1,
        interPodCardDefinitionIds: [10],
      }),
    )
    expect(payload).not.toHaveProperty('configuredPlayerCount')

    expect(pushSpy).toHaveBeenCalledWith({
      name: 'dashboard',
      params: { eventId: 99 },
    })
  })

  it('shows error if createEvent fails', async () => {
    const wrapper = mountView()

    const error = new Error('fail')
    error.response = { data: { message: 'Error al crear evento.' } }
    createEvent.mockRejectedValue(error)

    await new Promise((r) => setTimeout(r, 0))
    await wrapper.find('#name').setValue('Event')
    await wrapper.find('#villain').setValue('1')
    await wrapper.find('form').trigger('submit')

    await new Promise((r) => setTimeout(r, 10))

    expect(wrapper.find('.form-error').text()).toBe('Error al crear evento.')
  })

  it('shows link to my events page', () => {
    const wrapper = mountView()
    const link = wrapper.find('a[href="/my-events"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Volver a mis eventos')
  })
})
