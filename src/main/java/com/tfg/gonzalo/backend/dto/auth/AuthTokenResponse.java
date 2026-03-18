package com.tfg.gonzalo.backend.dto.auth;

public record AuthTokenResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        String role,
        Long eventId,
        Long groupId,
        String username,
        String displayName
) {
}
