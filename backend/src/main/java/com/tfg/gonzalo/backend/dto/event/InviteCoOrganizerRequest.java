package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record InviteCoOrganizerRequest(
        @NotBlank @Size(max = 60) String username,
        @NotEmpty Set<CoOrganizerResponsibility> responsibilities
) {
}