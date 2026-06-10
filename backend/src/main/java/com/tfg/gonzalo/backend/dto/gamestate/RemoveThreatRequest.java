package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;

public record RemoveThreatRequest(
        @Min(1) Integer threat
) {
}
