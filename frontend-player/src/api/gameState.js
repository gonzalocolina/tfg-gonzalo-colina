import http from './http'

export function getGameState(eventId) {
  return http.get(`/events/${eventId}/game-state`)
}

export function reportVillainDamage(eventId, data) {
  return http.post(`/events/${eventId}/game-state/villain-damage`, data)
}

export function reportThreat(eventId, data) {
  return http.post(`/events/${eventId}/game-state/threat`, data)
}

export function removeThreat(eventId, data) {
  return http.post(`/events/${eventId}/game-state/threat/remove`, data)
}
