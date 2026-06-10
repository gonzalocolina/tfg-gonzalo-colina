import http from './http'

export function getInterPodCardDefinitions() {
  return http.get('/inter-pod-card-definitions')
}

export function getEventInterPodCardDefinitions(eventId) {
  return http.get(`/events/${eventId}/inter-pod-card-definitions`)
}

export function createInterPodCardDefinition(data, imageFile) {
  const formData = new FormData()
  formData.append('name', data.name)
  formData.append('cardCategory', data.cardCategory)
  formData.append('defaultMetricMax', String(data.defaultMetricMax ?? 0))
  formData.append('ruleType', data.ruleType || 'MULTIPLIER')
  if (data.imageUrl?.trim()) {
    formData.append('imageUrl', data.imageUrl.trim())
  }
  if (imageFile) {
    formData.append('image', imageFile)
  }
  return http.post('/inter-pod-card-definitions', formData)
}

export function updateInterPodCardDefinition(definitionId, data, imageFile) {
  const formData = new FormData()
  formData.append('name', data.name)
  formData.append('cardCategory', data.cardCategory)
  formData.append('defaultMetricMax', String(data.defaultMetricMax ?? 0))
  formData.append('ruleType', data.ruleType || 'MULTIPLIER')
  if (data.imageUrl?.trim()) {
    formData.append('imageUrl', data.imageUrl.trim())
  }
  if (imageFile) {
    formData.append('image', imageFile)
  }
  return http.put(`/inter-pod-card-definitions/${definitionId}`, formData)
}

export function deleteInterPodCardDefinition(definitionId) {
  return http.delete(`/inter-pod-card-definitions/${definitionId}`)
}
