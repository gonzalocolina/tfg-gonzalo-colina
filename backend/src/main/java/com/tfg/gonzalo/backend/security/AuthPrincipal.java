package com.tfg.gonzalo.backend.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthPrincipal(
        Long userId,
        String username,
        String role,
        Long eventId,
        Long groupId,
    String codeKind
) {

    public Collection<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public boolean isOrganizer() {
        return "ORGANIZER".equals(role);
    }

    public boolean isCoOrganizer() {
        return "CO_ORGANIZER".equals(role);
    }

    public boolean isOrganizerLike() {
        return isOrganizer() || isCoOrganizer();
    }

    public boolean isRepresentative() {
        return "GROUP_REPRESENTATIVE".equals(role);
    }

    public boolean isObserver() {
        return "GROUP_OBSERVER".equals(role);
    }
}
