package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DelegatedVillainDamageRequest(
        @NotNull Long groupId,
        @NotNull @Min(1) Integer damage,
        @Size(max = 300) String justification
) {
}