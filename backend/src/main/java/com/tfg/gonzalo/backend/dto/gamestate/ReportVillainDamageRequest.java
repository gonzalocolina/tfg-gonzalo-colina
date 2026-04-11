package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportVillainDamageRequest(
        @NotBlank @Size(max = 16) String representativeCode,
        @Min(1) Integer damage
) {
}