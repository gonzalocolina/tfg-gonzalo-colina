import http from './http'

export function getEventInterPodCardDefinitions(eventId) {
  return http.get(`/events/${eventId}/inter-pod-card-definitions`)
}
