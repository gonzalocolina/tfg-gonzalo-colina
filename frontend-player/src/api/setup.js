import http from './http'

export function getEventStructure(eventId) {
  return http.get(`/events/${eventId}/structure`)
}
