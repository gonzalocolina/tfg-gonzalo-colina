package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateInterPodCardStatusRequest(
        @NotNull InterPodCardStatus status
) {
}
