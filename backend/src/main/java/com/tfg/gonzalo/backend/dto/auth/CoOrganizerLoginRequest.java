package com.tfg.gonzalo.backend.dto.auth;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoOrganizerLoginRequest(
        @Min(1) Long eventId,
        @NotBlank @Size(max = 60) String username,
        @NotBlank @Size(min = 8, max = 120) String password
) {
}
