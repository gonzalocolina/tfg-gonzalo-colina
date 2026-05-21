package com.tfg.gonzalo.backend.dto.event;

import jakarta.validation.constraints.Size;

public record RevertEventLogRequest(
        @Size(max = 300) String justification
) {
}