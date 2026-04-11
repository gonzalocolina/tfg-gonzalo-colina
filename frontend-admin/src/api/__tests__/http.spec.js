import { describe, it, expect, vi, beforeEach } from 'vitest'

// We test the http module indirectly by checking interceptor behavior
describe('HTTP Interceptors', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('adds Authorization header when auth token exists in sessionStorage', async () => {
    sessionStorage.setItem(
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

  it('clears sessionStorage and redirects on 401 response', async () => {
    sessionStorage.setItem('auth', JSON.stringify({ accessToken: 'tok' }))

    const { default: http } = await import('@/api/http')

    const interceptors = http.interceptors.response.handlers
    const responseInterceptor = interceptors[interceptors.length - 1]

    const mockReplace = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { replace: mockReplace },
      writable: true,
    })

    const error = { response: { status: 401 } }

    await expect(responseInterceptor.rejected(error)).rejects.toEqual(error)
    expect(sessionStorage.getItem('auth')).toBeNull()
    expect(mockReplace).toHaveBeenCalledWith('/login')
  })
})
