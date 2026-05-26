import http from './http'

export function getEventLogs(eventId, filters = {}) {
  const params = {}
  if (filters.groupId) params.groupId = filters.groupId
  if (filters.actionType) params.actionType = filters.actionType
  return http.get(`/events/${eventId}/logs`, { params })
}

export function revertEventLog(eventId, logId, data = {}) {
  return http.post(`/events/${eventId}/logs/${logId}/revert`, data)
}
