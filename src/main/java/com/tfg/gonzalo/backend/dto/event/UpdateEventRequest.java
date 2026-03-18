package com.tfg.gonzalo.backend.dto.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEventRequest(
        @NotBlank @Size(max = 120) String name,
        @Min(1) Integer configuredPlayerCount,
        @Min(0) @Max(600) Integer timeLimitMinutes
) {
}