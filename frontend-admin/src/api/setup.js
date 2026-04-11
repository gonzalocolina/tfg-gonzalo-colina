import http from './http'

export function createPod(eventId, data) {
  return http.post(`/events/${eventId}/pods`, data)
}

export function updatePod(eventId, podId, data) {
  return http.patch(`/events/${eventId}/pods/${podId}`, data)
}

export function deletePod(eventId, podId) {
  return http.delete(`/events/${eventId}/pods/${podId}`)
}

export function createGroup(eventId, podId, data) {
  return http.post(`/events/${eventId}/pods/${podId}/groups`, data)
}

export function assignGroupToPod(eventId, groupId, podId) {
  return http.patch(`/events/${eventId}/groups/${groupId}/pod`, { podId })
}

export function updateGroup(eventId, groupId, data) {
  return http.patch(`/events/${eventId}/groups/${groupId}`, data)
}

export function getEventStructure(eventId) {
  return http.get(`/events/${eventId}/structure`)
}
