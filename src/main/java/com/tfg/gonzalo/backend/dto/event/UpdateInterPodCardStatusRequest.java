package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateInterPodCardStatusRequest(
        @Size(max = 16) String representativeCode,
        @NotNull InterPodCardStatus status
) {
}