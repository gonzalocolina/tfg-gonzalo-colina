package com.tfg.gonzalo.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 60) String username,
        @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 120) String password
) {
}
