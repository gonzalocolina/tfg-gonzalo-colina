package com.tfg.gonzalo.backend.dto.setup;

import com.tfg.gonzalo.backend.model.enums.EventStatus;
import java.util.List;

public record EventStructureResponse(
        Long eventId,
        String eventName,
        EventStatus eventStatus,
        Integer configuredPlayerCount,
        Integer currentGroupCount,
        Integer assignedPlayerCount,
        Integer unassignedGroupCount,
        Integer unassignedPlayerCount,
        Integer schemeThreatTarget,
        String suggestedPodName,
        List<PodResponse> pods,
        List<GroupResponse> unassignedGroups
) {
}