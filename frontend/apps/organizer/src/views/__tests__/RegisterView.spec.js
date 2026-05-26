import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import RegisterView from '@/views/RegisterView.vue'
import { useAuthStore } from '@/stores/auth'

function createTestRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/my-events' },
      { path: '/login', name: 'login', component: { template: '<div>Login</div>' } },
      { path: '/register', name: 'register', component: { template: '<div>Register</div>' } },
      { path: '/my-events', name: 'my-events', component: { template: '<div>My Events</div>' } },
    ],
  })
}

describe('RegisterView', () => {
  let pinia
  let router

  beforeEach(() => {
    sessionStorage.clear()
    vi.clearAllMocks()
    pinia = createPinia()
    setActivePinia(pinia)
    router = createTestRouter()
  })

  function mountRegister() {
    return mount(RegisterView, {
      global: {
        plugins: [pinia, router],
      },
    })
  }

  it('renders a single username field and no display name field', () => {
    const wrapper = mountRegister()

    expect(wrapper.find('#username').exists()).toBe(true)
    expect(wrapper.find('#displayName').exists()).toBe(false)
    expect(wrapper.find('#email').exists()).toBe(true)
    expect(wrapper.find('#password').exists()).toBe(true)
  })

  it('shows the link back to login', () => {
    const wrapper = mountRegister()
    const link = wrapper.find('a[href="/login"]')

    expect(link.exists()).toBe(true)
    expect(link.text()).toContain('Iniciar sesión')
  })

  it('calls registerAndLogin with username, email and password', async () => {
    const wrapper = mountRegister()
    const auth = useAuthStore()

    auth.registerAndLogin = vi.fn(async () => {
      auth.accessToken = 'token'
      auth.role = 'ORGANIZER'
      auth.username = 'new-organizer'
    })

    await wrapper.find('#username').setValue('new-organizer')
    await wrapper.find('#email').setValue('new@example.com')
    await wrapper.find('#password').setValue('Password123')
    await wrapper.find('form').trigger('submit')
    await vi.dynamicImportSettled()

    expect(auth.registerAndLogin).toHaveBeenCalledWith('new-organizer', 'new@example.com', 'Password123')
  })

  it('shows backend error message on failed registration', async () => {
    const wrapper = mountRegister()
    const auth = useAuthStore()
    const error = new Error('fail')
    error.response = { data: { message: 'Ya existe un usuario con ese nombre de usuario.' } }

    auth.registerAndLogin = vi.fn().mockRejectedValue(error)

    await wrapper.find('#username').setValue('duplicated')
    await wrapper.find('#password').setValue('Password123')
    await wrapper.find('form').trigger('submit')
    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(wrapper.find('.form-error').text()).toBe('Ya existe un usuario con ese nombre de usuario.')
  })
})