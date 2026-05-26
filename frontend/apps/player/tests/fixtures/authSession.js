const STORAGE_KEY = 'auth-player'

const defaultSession = {
  accessToken: 'tok-123',
  role: 'GROUP_REPRESENTATIVE',
  eventId: 1,
  groupId: 20,
  groupLabel: 'Mesa Beta',
  code: 'REP001',
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
