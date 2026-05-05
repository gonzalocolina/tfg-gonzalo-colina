import http from './http'

export function getMyCoOrganizerInvitations() {
  return http.get('/co-organizer-invitations/mine')
}

export function acceptCoOrganizerInvitation(invitationId) {
  return http.post(`/co-organizer-invitations/${invitationId}/accept`)
}

export function rejectCoOrganizerInvitation(invitationId) {
  return http.post(`/co-organizer-invitations/${invitationId}/reject`)
}