import http from './http'

export function assignCoOrganizer(eventId, data) {
  return http.post(`/events/${eventId}/roles/co-organizers`, data)
}

export function getCoOrganizers(eventId) {
  return http.get(`/events/${eventId}/roles/co-organizers`)
}
