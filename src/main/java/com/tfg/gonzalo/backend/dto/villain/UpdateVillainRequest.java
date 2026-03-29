package com.tfg.gonzalo.backend.dto.villain;

import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVillainRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull VillainRuleType hpRuleType,
        @Min(0) Integer hpRuleValue,
        @NotNull VillainRuleType threatRuleType,
        @Min(0) Integer threatRuleValue
) {
}