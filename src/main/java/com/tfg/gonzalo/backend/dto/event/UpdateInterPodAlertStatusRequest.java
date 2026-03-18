package com.tfg.gonzalo.backend.dto.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateInterPodAlertStatusRequest(
        @NotBlank @Size(max = 255) String status
) {
}
