package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardType;
import java.time.Instant;

public record InterPodAlertResponse(
        Long id,
        Long eventId,
        Long podId,
        String podName,
        Long sourceGroupId,
        String sourceGroupIdentifier,
        InterPodCardType cardType,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
