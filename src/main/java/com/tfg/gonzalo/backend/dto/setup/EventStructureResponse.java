package com.tfg.gonzalo.backend.dto.setup;

import java.util.List;

public record EventStructureResponse(
        Long eventId,
        String eventName,
        Integer configuredPlayerCount,
        Integer currentGroupCount,
        Integer assignedPlayerCount,
        Integer schemeThreatTarget,
        List<PodResponse> pods
) {
}