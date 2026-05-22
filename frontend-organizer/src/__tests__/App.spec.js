import { beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import App from '../App.vue'
import { useAuthStore } from '@/stores/auth'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My events</div>' } },
      { path: '/create-event', name: 'create-event', component: { template: '<div>Create event</div>' } },
      { path: '/invitations', name: 'invitations', component: { template: '<div>Invitations</div>' } },
      { path: '/villains', name: 'villains', component: { template: '<div>Villains</div>' } },
    ],
  })
}

async function mountApp(initialPath, { authenticated = true } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)

  if (authenticated) {
    const auth = useAuthStore()
    auth.setSession({
      accessToken: 'tok-123',
      role: 'ORGANIZER',
      eventId: null,
      groupId: null,
      username: 'organizer',
    })
  }

  const router = createTestRouter()
  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(App, {
    global: {
      plugins: [pinia, router],
      stubs: {
        AppSidebar: {
          template: '<aside data-test="app-sidebar" />',
        },
        DialogHost: {
          template: '<div data-test="dialog-host" />',
        },
        NotificationToast: {
          template: '<div data-test="notification-toast" />',
        },
      },
    },
  })

  await flushPromises()

  return wrapper
}

describe('App layout', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('hides the sidebar on the invitations route', async () => {
    const wrapper = await mountApp('/invitations')

    expect(wrapper.find('[data-test="app-sidebar"]').exists()).toBe(false)
    expect(wrapper.find('main').classes()).not.toContain('app-main')
    expect(wrapper.text()).toContain('Invitations')

    wrapper.unmount()
  })

  it('keeps the sidebar on authenticated organizer routes outside invitations', async () => {
    const wrapper = await mountApp('/villains')

    expect(wrapper.find('[data-test="app-sidebar"]').exists()).toBe(true)
    expect(wrapper.find('main').classes()).toContain('app-main')
    expect(wrapper.text()).toContain('Villains')

    wrapper.unmount()
  })
})