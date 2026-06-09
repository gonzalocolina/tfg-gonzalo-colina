package com.tfg.gonzalo.backend.security;

import com.tfg.gonzalo.backend.exception.ForbiddenOperationException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;

    public AccessControlService(CoOrganizerInvitationRepository coOrganizerInvitationRepository) {
        this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
    }

    public AuthPrincipal requireAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ForbiddenOperationException("No tienes permisos para realizar esta acción.");
        }
        return principal;
    }

    public void requireOrganizerForEvent(Event event) {
        AuthPrincipal principal = requireAuthenticatedPrincipal();
        if (!principal.isOrganizer()) {
            throw new ForbiddenOperationException("Solo el organizador puede realizar esta operación.");
        }
        if (principal.userId() == null || !principal.userId().equals(event.getOrganizer().getId())) {
            throw new ForbiddenOperationException("No eres el organizador propietario de este evento.");
        }
    }

    public void requireReadAccessToEvent(Event event) {
        AuthPrincipal principal = requireAuthenticatedPrincipal();
        if (hasOwnerAccess(event, principal) || hasCoOrganizerAccess(event, principal)) {
            return;
        }
        if (principal.eventId() == null || !principal.eventId().equals(event.getId())) {
            throw new ForbiddenOperationException("El token no pertenece al evento solicitado.");
        }
    }

    public void requireOrganizerOrCoOrganizerForEvent(Event event) {
        AuthPrincipal principal = requireAuthenticatedPrincipal();
        if (hasOwnerAccess(event, principal) || hasCoOrganizerAccess(event, principal)) {
            return;
        }
        throw new ForbiddenOperationException("Solo organizador o co-organizador pueden realizar esta operación.");
    }

    public void requireGlobalResponsibility(Event event, CoOrganizerResponsibility responsibility) {
        AuthPrincipal principal = requireAuthenticatedPrincipal();
        if (hasOwnerAccess(event, principal)) {
            return;
        }
        if (!hasResponsibilityAccess(event, principal, responsibility)) {
            throw new ForbiddenOperationException("El co-organizador no tiene permiso para esta responsabilidad.");
        }
    }

    private boolean hasOwnerAccess(Event event, AuthPrincipal principal) {
        return principal.isOrganizer()
                && principal.userId() != null
                && principal.userId().equals(event.getOrganizer().getId());
    }

    private boolean hasCoOrganizerAccess(Event event, AuthPrincipal principal) {
        if (principal.userId() == null) {
            return false;
        }
        if (principal.isOrganizer()) {
            return coOrganizerInvitationRepository.existsByEventIdAndInviteeIdAndStatus(
                    event.getId(),
                    principal.userId(),
                    CoOrganizerInvitationStatus.ACCEPTED
            );
        }
        if (principal.isCoOrganizer()) {
            return principal.eventId() != null
                    && principal.eventId().equals(event.getId())
                    && coOrganizerInvitationRepository.existsByEventIdAndInviteeIdAndStatus(
                            event.getId(),
                            principal.userId(),
                            CoOrganizerInvitationStatus.ACCEPTED
                    );
        }
        return false;
    }

    private boolean hasResponsibilityAccess(Event event,
                                            AuthPrincipal principal,
                                            CoOrganizerResponsibility responsibility) {
        if (principal.userId() == null) {
            return false;
        }
        if (principal.isCoOrganizer()
                && (principal.eventId() == null || !principal.eventId().equals(event.getId()))) {
            return false;
        }
        return coOrganizerInvitationRepository.findByEventIdAndStatusOrderByCreatedAtDesc(
                event.getId(),
                CoOrganizerInvitationStatus.ACCEPTED
            ).stream()
            .anyMatch(invitation -> invitation.getInvitee().getId().equals(principal.userId())
                && invitation.getResponsibilities().contains(responsibility));
    }

    public void requireRepresentativeOrOrganizerForGroup(Event event, EventGroup group, String representativeCode) {
        AuthPrincipal principal = requireAuthenticatedPrincipal();
        if (principal.isOrganizer()) {
            if (principal.userId() != null && principal.userId().equals(event.getOrganizer().getId())) {
                return;
            }
            throw new ForbiddenOperationException("No eres el organizador propietario de este evento.");
        }

        requireRepresentativeForGroup(event, group, representativeCode);
    }

    public void requireRepresentativeForGroup(Event event, EventGroup group, String representativeCode) {
        AuthPrincipal principal = requireAuthenticatedPrincipal();
        if (!principal.isRepresentative()) {
            throw new ForbiddenOperationException("Solo el representante de la mesa puede reportar esta acción.");
        }
        if (principal.eventId() == null || !principal.eventId().equals(event.getId())) {
            throw new ForbiddenOperationException("El token no pertenece al evento solicitado.");
        }
        if (principal.groupId() == null || !principal.groupId().equals(group.getId())) {
            throw new ForbiddenOperationException("El token no pertenece a la mesa indicado.");
        }
        if (!group.getRepresentativeCode().equals(representativeCode)) {
            throw new ForbiddenOperationException("El código enviado no coincide con el representante autenticado.");
        }
    }
}
