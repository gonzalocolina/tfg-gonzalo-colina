import { describe, it, expect, beforeEach, afterEach } from 'vitest'

// We test the interceptor logic in isolation
describe('http interceptors', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  afterEach(() => {
    sessionStorage.clear()
  })

  it('injects Bearer token from sessionStorage', async () => {
    sessionStorage.setItem(
      'auth-player',
      JSON.stringify({ accessToken: 'test-token-123' }),
    )

    // Re-import to pick up sessionStorage
    const { default: http } = await import('../http')

    // Intercept the request config manually
    const config = { headers: {} }
    const interceptor = http.interceptors.request.handlers[0].fulfilled
    const result = interceptor(config)

    expect(result.headers.Authorization).toBe('Bearer test-token-123')
  })

  it('does not inject token when sessionStorage is empty', async () => {
    const { default: http } = await import('../http')

    const config = { headers: {} }
    const interceptor = http.interceptors.request.handlers[0].fulfilled
    const result = interceptor(config)

    expect(result.headers.Authorization).toBeUndefined()
  })
})
