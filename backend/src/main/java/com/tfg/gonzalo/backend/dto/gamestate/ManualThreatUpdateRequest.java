package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualThreatUpdateRequest(
        @NotNull @Min(0) Integer value,
        @Size(max = 300) String justification
) {
}