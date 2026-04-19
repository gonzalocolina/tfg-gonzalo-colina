package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.CoOrganizerInvitationResponse;
import com.tfg.gonzalo.backend.service.CoOrganizerInvitationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/co-organizer-invitations")
public class CoOrganizerInvitationController {

    private final CoOrganizerInvitationService coOrganizerInvitationService;

    public CoOrganizerInvitationController(CoOrganizerInvitationService coOrganizerInvitationService) {
        this.coOrganizerInvitationService = coOrganizerInvitationService;
    }

    @GetMapping("/mine")
    public List<CoOrganizerInvitationResponse> listMine() {
        return coOrganizerInvitationService.listMyPendingInvitations();
    }

    @PostMapping("/{invitationId}/accept")
    public CoOrganizerInvitationResponse accept(@PathVariable Long invitationId) {
        return coOrganizerInvitationService.acceptInvitation(invitationId);
    }

    @PostMapping("/{invitationId}/reject")
    public CoOrganizerInvitationResponse reject(@PathVariable Long invitationId) {
        return coOrganizerInvitationService.rejectInvitation(invitationId);
    }
}