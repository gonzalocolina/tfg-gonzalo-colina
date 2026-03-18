package com.tfg.gonzalo.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupLoginRequest(
        @NotNull Long eventId,
        @NotBlank @Size(max = 16) String code
) {
}
