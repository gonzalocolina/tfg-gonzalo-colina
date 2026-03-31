package com.tfg.gonzalo.backend.dto.setup;

import jakarta.validation.constraints.NotNull;

public record AssignGroupPodRequest(
        @NotNull Long podId
) {
}
