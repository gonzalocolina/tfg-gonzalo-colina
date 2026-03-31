package com.tfg.gonzalo.backend.dto.setup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @NotBlank @Size(max = 40) String identifier
) {
}
