import { describe, it, expect, beforeEach } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'

function createTestRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/login' },
      { path: '/login', name: 'login', component: { template: '<div/>' }, meta: { public: true } },
      {
        path: '/create-event',
        name: 'create-event',
        component: { template: '<div/>' },
        meta: { public: true },
      },
      { path: '/event/:eventId', name: 'dashboard', component: { template: '<div/>' } },
      { path: '/event/:eventId/setup', name: 'setup', component: { template: '<div/>' } },
      { path: '/event/:eventId/statistics', name: 'statistics', component: { template: '<div/>' } },
    ],
  })

  // Replicate the guard from the real router
  router.beforeEach((to, _from, next) => {
    if (to.meta.public) {
      next()
      return
    }

    const raw = sessionStorage.getItem('auth')
    if (!raw) {
      next({ name: 'login' })
      return
    }

    try {
      const { accessToken } = JSON.parse(raw)
      if (!accessToken) {
        next({ name: 'login' })
        return
      }
    } catch {
      next({ name: 'login' })
      return
    }

    next()
  })

  return router
}

describe('Router Guard', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('allows access to public routes without auth', async () => {
    const router = createTestRouter()
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows access to create-event without auth', async () => {
    const router = createTestRouter()
    await router.push('/create-event')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('create-event')
  })

  it('redirects to login when accessing protected route without auth', async () => {
    const router = createTestRouter()
    await router.push('/event/1')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows access to protected route with valid auth', async () => {
    sessionStorage.setItem('auth', JSON.stringify({ accessToken: 'valid-token' }))
    const router = createTestRouter()
    await router.push('/event/1')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('redirects to login when token is empty', async () => {
    sessionStorage.setItem('auth', JSON.stringify({ accessToken: '' }))
    const router = createTestRouter()
    await router.push('/event/1/setup')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('redirects to login when sessionStorage has invalid JSON', async () => {
    sessionStorage.setItem('auth', 'not-json')
    const router = createTestRouter()
    await router.push('/event/1')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows access to statistics route with valid auth', async () => {
    sessionStorage.setItem('auth', JSON.stringify({ accessToken: 'valid-token' }))
    const router = createTestRouter()
    await router.push('/event/1/statistics')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('statistics')
  })
})
