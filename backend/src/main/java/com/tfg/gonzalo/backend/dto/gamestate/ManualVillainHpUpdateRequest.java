package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;

public record ManualVillainHpUpdateRequest(
        @Min(0) Integer value
) {
}