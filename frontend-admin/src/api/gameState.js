import http from './http'

export function getGameState(eventId) {
  return http.get(`/events/${eventId}/game-state`)
}

export function startGame(eventId) {
  return http.post(`/events/${eventId}/game-state/start`)
}

export function reportVillainDamage(eventId, data) {
  return http.post(`/events/${eventId}/game-state/villain-damage`, data)
}

export function reportThreat(eventId, data) {
  return http.post(`/events/${eventId}/game-state/threat`, data)
}

export function manualUpdateVillainHp(eventId, value) {
  return http.patch(`/events/${eventId}/game-state/villain-hp`, { value })
}

export function manualUpdateThreat(eventId, value) {
  return http.patch(`/events/${eventId}/game-state/threat`, { value })
}

export function startTimer(eventId) {
  return http.post(`/events/${eventId}/game-state/timer/start`)
}

export function pauseTimer(eventId) {
  return http.post(`/events/${eventId}/game-state/timer/pause`)
}

export function resumeTimer(eventId) {
  return http.post(`/events/${eventId}/game-state/timer/resume`)
}

export function adjustTimer(eventId, minutesDelta) {
  return http.post(`/events/${eventId}/game-state/timer/adjust`, { minutesDelta })
}
