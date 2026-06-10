package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Min;

public record ReportVillainDamageRequest(
        @Min(1) Integer damage
) {
}
