import http from './http'

export function getVillains() {
  return http.get('/villains')
}

export function createVillain(data) {
  return http.post('/villains', data)
}

export function updateVillain(villainId, data) {
  return http.put(`/villains/${villainId}`, data)
}

export function deleteVillain(villainId) {
  return http.delete(`/villains/${villainId}`)
}
