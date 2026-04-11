package com.tfg.gonzalo.backend.dto.setup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreatePodRequest(
        @NotBlank @Size(max = 80) String name,
        @PositiveOrZero Integer sortOrder
) {
}