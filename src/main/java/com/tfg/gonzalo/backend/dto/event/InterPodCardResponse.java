package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import com.tfg.gonzalo.backend.model.enums.InterPodCardType;
import java.time.Instant;

public record InterPodCardResponse(
        Long id,
        Long eventId,
        Long podId,
        String podName,
        Long sourceGroupId,
        String sourceGroupIdentifier,
        InterPodCardType cardName,
        InterPodCardCategory cardCategory,
        Integer hpMax,
        Integer hpCurrent,
        InterPodCardStatus status,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt
) {
}