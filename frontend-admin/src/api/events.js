import http from './http'

export function getMyEvents() {
  return http.get('/events/mine')
}

export function createEvent(data) {
  return http.post('/events', data)
}

export function getEvent(eventId) {
  return http.get(`/events/${eventId}`)
}

export function updateEvent(eventId, data) {
  return http.put(`/events/${eventId}`, data)
}

export function deleteEvent(eventId) {
  return http.delete(`/events/${eventId}`)
}
