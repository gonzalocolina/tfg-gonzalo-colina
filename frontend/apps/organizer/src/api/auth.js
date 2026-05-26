import http from './http'

export function register(username, email, password) {
  return http.post('/auth/register', { username, email, password })
}

export function organizerLogin(username, password) {
  return http.post('/auth/organizer/login', { username, password })
}

export function coOrganizerLogin(eventId, username, password) {
  return http.post('/auth/co-organizer/login', { eventId, username, password })
}
