package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;

public record ManualThreatUpdateRequest(
        @Min(0) Integer value
) {
}