package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import java.util.Set;

public record CoOrganizerResponse(
        Long userId,
        String username,
        String displayName,
        String email,
        Set<CoOrganizerResponsibility> responsibilities
) {
}
