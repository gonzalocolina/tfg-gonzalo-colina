package com.tfg.gonzalo.backend.dto.setup;

public record GroupResponse(
        Long id,
        Long podId,
        String identifier,
        Integer playerCount,
        String representativeCode,
        String observerCode
) {
}