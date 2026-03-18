package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInterPodAlertRequest(
        @NotBlank @Size(max = 16) String representativeCode,
        @NotNull InterPodCardType cardType,
        @Size(max = 255) String status
) {
}
