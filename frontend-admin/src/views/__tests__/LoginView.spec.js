import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

// Mock the API
vi.mock('@/api/auth', () => ({
  adminLogin: vi.fn(),
  coOrganizerLogin: vi.fn(),
  register: vi.fn(),
}))

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/my-events' },
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
      { path: '/create-event', name: 'create-event', component: { template: '<div>Create</div>' } },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div>Dashboard</div>' } },
    ],
  })
}

describe('LoginView', () => {
  let pinia
  let router

  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    router = createTestRouter()
  })

  function mountLogin() {
    return mount(LoginView, {
      global: {
        plugins: [pinia, router],
      },
    })
  }

  it('renders the login form', () => {
    const wrapper = mountLogin()
    expect(wrapper.find('h1').text()).toContain('God of Lies')
    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
    expect(wrapper.find('button').text()).toBe('Iniciar sesión')
  })

  it('shows the link to register', () => {
    const wrapper = mountLogin()
    const link = wrapper.find('a[href="/register"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Registrarse')
  })

  it('calls loginAdmin on form submit and redirects to dashboard', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()

    // Mock loginAdmin to set state
    auth.loginAdmin = vi.fn(async () => {
      auth.accessToken = 'token'
      auth.role = 'ORGANIZER'
      auth.eventId = 42
      auth.username = 'admin'
      auth.displayName = 'Admin'
    })

    await wrapper.find('#username').setValue('admin')
    await wrapper.find('#password').setValue('password123')
    await wrapper.find('form').trigger('submit')

    // Wait for async
    await vi.dynamicImportSettled()

    expect(auth.loginAdmin).toHaveBeenCalledWith('admin', 'password123')
  })

  it('redirects to my-events after login', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    const pushSpy = vi.spyOn(router, 'push')

    auth.loginAdmin = vi.fn(async () => {
      auth.accessToken = 'token'
      auth.role = 'ORGANIZER'
      auth.eventId = null
    })

    await wrapper.find('#username').setValue('admin')
    await wrapper.find('#password').setValue('pass')
    await wrapper.find('form').trigger('submit')
    await vi.dynamicImportSettled()

    expect(pushSpy).toHaveBeenCalledWith({ name: 'my-events' })
  })

  it('shows error message on failed login', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()

    const error = new Error('fail')
    error.response = { data: { message: 'Credenciales inválidas.' } }
    auth.loginAdmin = vi.fn().mockRejectedValue(error)

    await wrapper.find('#username').setValue('bad')
    await wrapper.find('#password').setValue('wrong')
    await wrapper.find('form').trigger('submit')

    // Wait for the catch block
    await new Promise((r) => setTimeout(r, 10))

    expect(wrapper.find('.form-error').text()).toBe('Credenciales inválidas.')
  })

  it('disables the button while loading', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()

    // Make loginAdmin a promise that never resolves (simulates loading)
    let resolveLogin
    auth.loginAdmin = vi.fn(() => new Promise((r) => { resolveLogin = r }))

    await wrapper.find('#username').setValue('admin')
    await wrapper.find('#password').setValue('pass')
    await wrapper.find('form').trigger('submit')

    // During loading
    await wrapper.vm.$nextTick()
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
    expect(wrapper.find('button').text()).toBe('Entrando…')

    // Resolve and cleanup
    resolveLogin({ accessToken: 'tok', eventId: 1 })
  })
})
