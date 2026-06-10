import http from './http'

export function getCoOrganizers(eventId) {
  return http.get(`/events/${eventId}/roles/co-organizers`)
}

export function inviteCoOrganizer(eventId, data) {
  return http.post(`/events/${eventId}/roles/co-organizer-invitations`, data)
}

export function getCoOrganizerInvitations(eventId) {
  return http.get(`/events/${eventId}/roles/co-organizer-invitations`)
}

export function revokeCoOrganizerInvitation(eventId, invitationId) {
  return http.delete(`/events/${eventId}/roles/co-organizer-invitations/${invitationId}`)
}
