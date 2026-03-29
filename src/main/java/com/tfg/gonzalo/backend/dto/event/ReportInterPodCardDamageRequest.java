package com.tfg.gonzalo.backend.dto.event;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportInterPodCardDamageRequest(
        @Size(max = 16) String representativeCode,
        @NotNull @Min(1) Integer damage
) {
}