import { describe, it, expect, beforeEach, vi } from 'vitest'

describe('router guards', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('allows access to /join without auth', async () => {
    const { default: router } = await import('../index')

    await router.push('/join')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('join')
  })

  it('redirects to /join when accessing /game without auth', async () => {
    // Need a fresh router import per test — dynamically re-import
    vi.resetModules()
    const { default: router } = await import('../index')
    const { createPinia } = await import('pinia')
    const { createApp } = await import('vue')
    const app = createApp({ template: '<router-view />' })
    app.use(createPinia())
    app.use(router)

    await router.push('/game')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('join')
  })

  it('allows access to /game with valid auth', async () => {
    sessionStorage.setItem(
      'auth-player',
      JSON.stringify({ accessToken: 'valid-token' }),
    )

    vi.resetModules()
    const { default: router } = await import('../index')
    const { createPinia } = await import('pinia')
    const { createApp } = await import('vue')
    const app = createApp({ template: '<router-view />' })
    app.use(createPinia())
    app.use(router)

    await router.push('/game')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('game')
  })
})
