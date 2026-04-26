package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record AssignCoOrganizerRequest(
        @NotBlank @Size(max = 60) String username,
        @Size(max = 120) String email,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotEmpty Set<CoOrganizerResponsibility> responsibilities
) {
}
