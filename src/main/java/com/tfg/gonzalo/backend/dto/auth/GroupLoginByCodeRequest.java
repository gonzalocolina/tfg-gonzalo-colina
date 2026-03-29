package com.tfg.gonzalo.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroupLoginByCodeRequest(
        @NotBlank @Size(max = 16) String code
) {
}
