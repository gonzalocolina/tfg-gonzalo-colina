package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInterPodCardRequest(
        @NotBlank @Size(max = 16) String representativeCode,
        Long interPodCardDefinitionId,
        @Size(max = 120) String cardName,
        @Min(1) Integer hpMax,
        InterPodCardStatus status
) {
}