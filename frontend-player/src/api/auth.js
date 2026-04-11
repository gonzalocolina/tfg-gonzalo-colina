import http from './http'

export function groupLogin(code, groupIdentifier, playerCount) {
  return http.post('/auth/group/login-by-code', {
    code,
    displayName: groupIdentifier || undefined,
    playerCount: playerCount || undefined,
  })
}
