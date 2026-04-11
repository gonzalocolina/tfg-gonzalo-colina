package com.tfg.gonzalo.backend.dto.villain;

import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import java.time.Instant;

public record VillainResponse(
        Long id,
        String name,
        VillainDefinitionType type,
        VillainRuleType hpRuleType,
        Integer hpRuleValue,
        VillainRuleType threatRuleType,
        Integer threatRuleValue,
        Long ownerId,
        String ownerUsername,
        Instant createdAt,
        Instant updatedAt
) {
}