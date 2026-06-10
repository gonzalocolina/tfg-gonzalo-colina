package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.CoOrganizerResponse;
import com.tfg.gonzalo.backend.dto.event.CoOrganizerInvitationResponse;
import com.tfg.gonzalo.backend.dto.event.InviteCoOrganizerRequest;
import com.tfg.gonzalo.backend.service.CoOrganizerInvitationService;
import com.tfg.gonzalo.backend.service.EventRoleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/roles")
public class EventRoleController {

    private final EventRoleService eventRoleService;
    private final CoOrganizerInvitationService coOrganizerInvitationService;

    public EventRoleController(EventRoleService eventRoleService,
                               CoOrganizerInvitationService coOrganizerInvitationService) {
        this.eventRoleService = eventRoleService;
        this.coOrganizerInvitationService = coOrganizerInvitationService;
    }

    @GetMapping("/co-organizers")
    public List<CoOrganizerResponse> listCoOrganizers(@PathVariable Long eventId) {
        return eventRoleService.listCoOrganizers(eventId);
    }

    @GetMapping("/co-organizer-invitations")
    public List<CoOrganizerInvitationResponse> listPendingInvitations(@PathVariable Long eventId) {
        return coOrganizerInvitationService.listPendingInvitationsForEvent(eventId);
    }

    @PostMapping("/co-organizer-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public CoOrganizerInvitationResponse inviteCoOrganizer(@PathVariable Long eventId,
                                                           @Valid @RequestBody InviteCoOrganizerRequest request) {
        return coOrganizerInvitationService.invite(eventId, request);
    }

    @DeleteMapping("/co-organizer-invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable Long eventId,
                                 @PathVariable Long invitationId) {
        coOrganizerInvitationService.revokeInvitation(eventId, invitationId);
    }
}
