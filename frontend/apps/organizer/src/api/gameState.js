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

export function delegateVillainDamage(eventId, data) {
  return http.post(`/events/${eventId}/game-state/villain-damage/delegated`, data)
}

export function delegateRemoveVillainDamage(eventId, data) {
  return http.post(`/events/${eventId}/game-state/villain-damage/remove/delegated`, data)
}

export function delegateThreat(eventId, data) {
  return http.post(`/events/${eventId}/game-state/threat/delegated`, data)
}

export function delegateRemoveThreat(eventId, data) {
  return http.post(`/events/${eventId}/game-state/threat/remove/delegated`, data)
}

export function manualUpdateVillainHp(eventId, valueOrData, justification) {
  const payload = typeof valueOrData === 'object' ? valueOrData : { value: valueOrData, justification }
  return http.patch(`/events/${eventId}/game-state/villain-hp`, payload)
}

export function manualUpdateThreat(eventId, valueOrData, justification) {
  const payload = typeof valueOrData === 'object' ? valueOrData : { value: valueOrData, justification }
  return http.patch(`/events/${eventId}/game-state/threat`, payload)
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
