package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import java.time.Instant;
import java.util.Set;

public record CoOrganizerInvitationResponse(
        Long id,
        Long eventId,
        String eventName,
        EventStatus eventStatus,
        Integer timeLimitMinutes,
        String organizerUsername,
        Long inviteeUserId,
        String inviteeUsername,
        String inviteeEmail,
        CoOrganizerInvitationStatus status,
        Set<CoOrganizerResponsibility> responsibilities,
        Instant createdAt
) {
}