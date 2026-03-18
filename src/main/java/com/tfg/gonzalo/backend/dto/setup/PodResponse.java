package com.tfg.gonzalo.backend.dto.setup;

import java.util.List;

public record PodResponse(
        Long id,
        String name,
        Integer sortOrder,
        Integer totalGroups,
        Integer totalPlayers,
        List<String> warnings,
        List<GroupResponse> groups
) {
}