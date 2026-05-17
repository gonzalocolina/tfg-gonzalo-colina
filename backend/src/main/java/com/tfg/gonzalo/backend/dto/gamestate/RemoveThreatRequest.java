package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RemoveThreatRequest(
        @NotBlank @Size(max = 16) String representativeCode,
        @NotNull @Min(1) Integer threat
) {
}