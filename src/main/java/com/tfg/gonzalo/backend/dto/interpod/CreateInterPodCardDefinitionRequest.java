package com.tfg.gonzalo.backend.dto.interpod;

import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInterPodCardDefinitionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull InterPodCardCategory cardCategory,
        @NotNull @Min(1) Integer defaultHpMax,
        InterPodCardStatus defaultStatus
) {
}
