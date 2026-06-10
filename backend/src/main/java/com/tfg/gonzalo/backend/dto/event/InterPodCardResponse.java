package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import java.time.Instant;

public record InterPodCardResponse(
        Long id,
        Long eventId,
        Long podId,
        String podName,
        Long sourceGroupId,
        String sourceGroupIdentifier,
        Long interPodCardDefinitionId,
        String cardName,
        InterPodCardCategory cardCategory,
        Integer metricMax,
        Integer metricCurrent,
        InterPodCardStatus status,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}
