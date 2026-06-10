import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

// Mock the auth API module
vi.mock('@/api/auth', () => ({
  organizerLogin: vi.fn(),
  register: vi.fn(),
}))

import { organizerLogin, register } from '@/api/auth'

describe('Auth Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    vi.clearAllMocks()
  })

  it('starts unauthenticated when sessionStorage is empty', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.accessToken).toBe('')
    expect(auth.role).toBe('')
    expect(auth.eventId).toBeNull()
  })

  it('restores session from sessionStorage', () => {
    sessionStorage.setItem(
      'auth',
      JSON.stringify({
        accessToken: 'tok123',
        role: 'ORGANIZER',
        eventId: 5,
        groupId: null,
        username: 'organizer',
      }),
    )
    // Need a fresh pinia to pick up the stored session
    setActivePinia(createPinia())
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.accessToken).toBe('tok123')
    expect(auth.role).toBe('ORGANIZER')
    expect(auth.eventId).toBe(5)
    expect(auth.username).toBe('organizer')
  })

  it('loginOrganizer calls API and stores the response', async () => {
    const mockResponse = {
      data: {
        accessToken: 'jwt-token',
        role: 'ORGANIZER',
        eventId: 10,
        groupId: null,
        username: 'organizer',
      },
    }
    organizerLogin.mockResolvedValue(mockResponse)

    const auth = useAuthStore()
    const result = await auth.loginOrganizer('organizer', 'password123')

    expect(organizerLogin).toHaveBeenCalledWith('organizer', 'password123')
    expect(auth.accessToken).toBe('jwt-token')
    expect(auth.role).toBe('ORGANIZER')
    expect(auth.eventId).toBe(10)
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.isOrganizer).toBe(true)
    expect(result).toEqual(mockResponse.data)

    // Check persistence
    const stored = JSON.parse(sessionStorage.getItem('auth'))
    expect(stored.accessToken).toBe('jwt-token')
    expect(stored.eventId).toBe(10)
  })

  it('loginOrganizer propagates API errors', async () => {
    const apiError = new Error('Network error')
    apiError.response = { status: 400, data: { message: 'Credenciales inválidas.' } }
    organizerLogin.mockRejectedValue(apiError)

    const auth = useAuthStore()
    await expect(auth.loginOrganizer('bad', 'wrong')).rejects.toThrow('Network error')
    expect(auth.isAuthenticated).toBe(false)
  })

  it('logout clears session state and sessionStorage', async () => {
    const mockResponse = {
      data: {
        accessToken: 'tok',
        role: 'ORGANIZER',
        eventId: 1,
        groupId: null,
        username: 'organizer',
      },
    }
    organizerLogin.mockResolvedValue(mockResponse)

    const auth = useAuthStore()
    await auth.loginOrganizer('organizer', 'pass')
    expect(auth.isAuthenticated).toBe(true)

    auth.logout()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.accessToken).toBe('')
    expect(auth.role).toBe('')
    expect(auth.eventId).toBeNull()
    expect(sessionStorage.getItem('auth')).toBeNull()
  })

  it('registerAndLogin calls the register API and persists the session', async () => {
    const mockResponse = {
      data: {
        accessToken: 'register-token',
        role: 'ORGANIZER',
        eventId: null,
        groupId: null,
        username: 'new-organizer',
      },
    }
    register.mockResolvedValue(mockResponse)

    const auth = useAuthStore()
    const result = await auth.registerAndLogin('new-organizer', 'new@example.com', 'Password123')

    expect(register).toHaveBeenCalledWith('new-organizer', 'new@example.com', 'Password123')
    expect(auth.username).toBe('new-organizer')
    expect(auth.isAuthenticated).toBe(true)
    expect(result).toEqual(mockResponse.data)
  })
})
