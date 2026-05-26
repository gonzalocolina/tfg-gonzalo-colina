const STORAGE_KEY = 'auth'

const defaultSession = {
  accessToken: 'tok-123',
  role: 'ORGANIZER',
  eventId: null,
  groupId: null,
  username: 'organizer',
}

function normalizeOverrides(roleOrOverrides = {}, overrides = {}) {
  return typeof roleOrOverrides === 'string'
    ? { role: roleOrOverrides, ...overrides }
    : roleOrOverrides
}

export function buildAuthSession(roleOrOverrides = {}, overrides = {}) {
  return {
    ...defaultSession,
    ...normalizeOverrides(roleOrOverrides, overrides),
  }
}

export function seedAuthSession(roleOrOverrides = {}, overrides = {}) {
  const session = buildAuthSession(roleOrOverrides, overrides)
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  return session
}
