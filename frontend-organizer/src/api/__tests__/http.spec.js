import { describe, it, expect, vi, beforeEach } from 'vitest'

// We test the http module indirectly by checking interceptor behavior
describe('HTTP Interceptors', () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear()
    vi.resetModules()
  })

  it('adds Authorization header when auth token exists in sessionStorage', async () => {
    globalThis.sessionStorage.setItem(
      'auth',
      JSON.stringify({ accessToken: 'my-token' }),
    )

    // Import fresh to pick up current sessionStorage
    const { default: http } = await import('@/api/http')

    // Simulate the request interceptor by calling it directly
    const interceptors = http.interceptors.request.handlers
    const requestInterceptor = interceptors[interceptors.length - 1]
    const config = { headers: {} }
    const result = requestInterceptor.fulfilled(config)

    expect(result.headers.Authorization).toBe('Bearer my-token')
  })

  it('does not add Authorization header when no token', async () => {
    const { default: http } = await import('@/api/http')

    const interceptors = http.interceptors.request.handlers
    const requestInterceptor = interceptors[interceptors.length - 1]
    const config = { headers: {} }
    const result = requestInterceptor.fulfilled(config)

    expect(result.headers.Authorization).toBeUndefined()
  })

  it('does not add Authorization header to auth endpoints even if a token exists', async () => {
    globalThis.sessionStorage.setItem('auth', JSON.stringify({ accessToken: 'my-token' }))

    const { default: http } = await import('@/api/http')

    const interceptors = http.interceptors.request.handlers
    const requestInterceptor = interceptors[interceptors.length - 1]
    const config = { url: '/auth/organizer/login', headers: {} }
    const result = requestInterceptor.fulfilled(config)

    expect(result.headers.Authorization).toBeUndefined()
  })

  it('clears sessionStorage and redirects on 401 response', async () => {
    globalThis.sessionStorage.setItem('auth', JSON.stringify({ accessToken: 'tok' }))

    const { default: http } = await import('@/api/http')

    const interceptors = http.interceptors.response.handlers
    const responseInterceptor = interceptors[interceptors.length - 1]

    const mockReplace = vi.fn()
    Object.defineProperty(globalThis, 'location', {
      value: { replace: mockReplace },
      writable: true,
    })

    const error = { response: { status: 401 }, config: { url: '/events/mine' } }

    await expect(responseInterceptor.rejected(error)).rejects.toEqual(error)
    expect(globalThis.sessionStorage.getItem('auth')).toBeNull()
    expect(mockReplace).toHaveBeenCalledWith('/login')
  })

  it('clears sessionStorage without redirecting on 401 from an auth endpoint', async () => {
    globalThis.sessionStorage.setItem('auth', JSON.stringify({ accessToken: 'tok' }))

    const { default: http } = await import('@/api/http')

    const interceptors = http.interceptors.response.handlers
    const responseInterceptor = interceptors[interceptors.length - 1]

    const mockReplace = vi.fn()
    Object.defineProperty(globalThis, 'location', {
      value: { replace: mockReplace },
      writable: true,
    })

    const error = { response: { status: 401 }, config: { url: '/auth/organizer/login' } }

    await expect(responseInterceptor.rejected(error)).rejects.toEqual(error)
    expect(globalThis.sessionStorage.getItem('auth')).toBeNull()
    expect(mockReplace).not.toHaveBeenCalled()
  })
})
