import http from './http'

export function createInterPodCard(eventId, payload, imageFile) {
  const formData = new FormData()
  formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
  if (imageFile) {
    formData.append('image', imageFile)
  }
  return http.post(`/events/${eventId}/inter-pod-cards`, formData)
}

export function reportInterPodCardProgress(eventId, cardId, data) {
  return http.post(`/events/${eventId}/inter-pod-cards/${cardId}/progress`, data)
}

export function updateInterPodCardStatus(eventId, cardId, data) {
  return http.patch(`/events/${eventId}/inter-pod-cards/${cardId}/status`, data)
}

export function deleteInterPodCard(eventId, cardId) {
  return http.delete(`/events/${eventId}/inter-pod-cards/${cardId}`)
}

export function getInterPodCards(eventId, podId) {
  const params = {}
  if (podId) params.podId = podId
  return http.get(`/events/${eventId}/inter-pod-cards`, { params })
}
