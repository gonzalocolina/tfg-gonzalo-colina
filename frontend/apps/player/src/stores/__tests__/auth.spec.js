import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '../auth'

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
  })

  afterEach(() => {
    sessionStorage.clear()
  })

  it('starts unauthenticated', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.isRepresentative).toBe(false)
    expect(auth.isObserver).toBe(false)
  })

  it('restores session from sessionStorage', () => {
    sessionStorage.setItem(
      'auth-player',
      JSON.stringify({
        accessToken: 'tok123',
        role: 'GROUP_REPRESENTATIVE',
        eventId: 1,
        groupId: 2,
        groupLabel: 'Mesa A',
        code: 'EVT001',
      }),
    )
    setActivePinia(createPinia())
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.isRepresentative).toBe(true)
    expect(auth.accessToken).toBe('tok123')
    expect(auth.code).toBe('EVT001')
  })

  it('logout clears state and storage', () => {
    sessionStorage.setItem(
      'auth-player',
      JSON.stringify({
        accessToken: 'tok',
        role: 'GROUP_OBSERVER',
        eventId: 1,
        groupId: 2,
        groupLabel: 'Mesa B',
        code: 'OBS001',
      }),
    )
    setActivePinia(createPinia())
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)

    auth.logout()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.accessToken).toBe('')
    expect(sessionStorage.getItem('auth-player')).toBeNull()
  })

  it('isObserver is true for GROUP_OBSERVER role', () => {
    sessionStorage.setItem(
      'auth-player',
      JSON.stringify({
        accessToken: 'tok',
        role: 'GROUP_OBSERVER',
        eventId: 1,
        groupId: 3,
        groupLabel: 'Mesa C',
        code: 'OBS002',
      }),
    )
    setActivePinia(createPinia())
    const auth = useAuthStore()
    expect(auth.isObserver).toBe(true)
    expect(auth.isRepresentative).toBe(false)
  })
})
