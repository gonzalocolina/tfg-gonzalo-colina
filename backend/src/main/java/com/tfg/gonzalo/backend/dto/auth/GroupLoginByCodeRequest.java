package com.tfg.gonzalo.backend.dto.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupLoginByCodeRequest(
        @NotBlank @Size(max = 16) String code,
        @Size(max = 40) String groupLabel,
        @Min(1) @Max(4) Integer playerCount
) {
}
