package com.tfg.gonzalo.backend.dto.setup;

import java.util.List;

public record EventStructureResponse(
        Long eventId,
        String eventName,
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