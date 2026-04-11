package com.tfg.gonzalo.backend.dto.gamestate;

import java.util.List;

public record GameStateOperationResponse(
        GameStateSnapshotResponse snapshot,
        List<String> notifications
) {
}