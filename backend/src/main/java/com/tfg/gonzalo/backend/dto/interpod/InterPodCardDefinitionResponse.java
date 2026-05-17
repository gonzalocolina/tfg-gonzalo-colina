package com.tfg.gonzalo.backend.dto.interpod;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardDefinitionType;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import java.time.Instant;

public record InterPodCardDefinitionResponse(
        Long id,
        String name,
        InterPodCardDefinitionType type,
        InterPodCardCategory cardCategory,
        Integer defaultHpMax,
        Integer defaultThreatMax,
        InterPodCardStatus defaultStatus,
        String imageUrl,
        Long ownerId,
        String ownerUsername,
        Instant createdAt,
        Instant updatedAt
) {
}
