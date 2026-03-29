package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import com.tfg.gonzalo.backend.model.enums.InterPodCardType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInterPodCardRequest(
        @NotBlank @Size(max = 16) String representativeCode,
        @NotNull InterPodCardType cardName,
        @NotNull @Min(1) Integer hpMax,
        InterPodCardStatus status
) {
}