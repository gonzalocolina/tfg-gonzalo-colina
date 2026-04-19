package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CoOrganizerInvitationResponse;
import com.tfg.gonzalo.backend.dto.event.InviteCoOrganizerRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ForbiddenOperationException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerInvitation;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerAssignmentRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoOrganizerInvitationService {

    private final EventRepository eventRepository;
    private final AppUserRepository appUserRepository;
    private final CoOrganizerAssignmentRepository coOrganizerAssignmentRepository;
    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;
    private final EventRoleService eventRoleService;
    private final EventLogService eventLogService;
    private final AccessControlService accessControlService;

    public CoOrganizerInvitationService(EventRepository eventRepository,
                                        AppUserRepository appUserRepository,
                                        CoOrganizerAssignmentRepository coOrganizerAssignmentRepository,
                                        CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                                        EventRoleService eventRoleService,
                                        EventLogService eventLogService,
                                        AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.appUserRepository = appUserRepository;
        this.coOrganizerAssignmentRepository = coOrganizerAssignmentRepository;
        this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.eventRoleService = eventRoleService;
        this.eventLogService = eventLogService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public CoOrganizerInvitationResponse invite(Long eventId, InviteCoOrganizerRequest request) {
        Event event = findManageableEvent(eventId);
        AppUser inviter = findAuthenticatedUser();
        String normalizedUsername = request.username().trim();
        AppUser invitee = appUserRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new BusinessRuleException("No existe ninguna cuenta registrada con ese nombre de usuario."));

        validateInvitee(event, inviter, invitee);
        ensurePendingInvitationSlotAvailable(eventId);

        CoOrganizerInvitation invitation = new CoOrganizerInvitation();
        invitation.setEvent(event);
        invitation.setInvitee(invitee);
        invitation.setInvitedBy(inviter);
        invitation.setStatus(CoOrganizerInvitationStatus.PENDING);
        invitation.setResponsibilities(normalizeResponsibilities(request.responsibilities()));
        invitation = coOrganizerInvitationRepository.save(invitation);

        eventLogService.appendOrganizerLog(
                event,
                EventActionType.CO_ORGANIZER_INVITATION_SENT,
                null,
                invitee.getUsername(),
                "Invitación enviada con responsabilidades: " + formatResponsibilities(invitation.getResponsibilities())
        );

        return toResponse(invitation);
    }

    @Transactional(readOnly = true)
    public List<CoOrganizerInvitationResponse> listPendingInvitationsForEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);

        return coOrganizerInvitationRepository.findByEventIdAndStatusOrderByCreatedAtDesc(
                        eventId,
                        CoOrganizerInvitationStatus.PENDING
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CoOrganizerInvitationResponse> listMyPendingInvitations() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (principal.userId() == null) {
            return List.of();
        }

        return coOrganizerInvitationRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(
                        principal.userId(),
                        CoOrganizerInvitationStatus.PENDING
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CoOrganizerInvitationResponse acceptInvitation(Long invitationId) {
        CoOrganizerInvitation invitation = findMine(invitationId);
        ensurePending(invitation);

        eventRoleService.ensureCoOrganizerSlotAvailable(invitation.getEvent().getId(), invitation.getInvitee().getId());
        eventRoleService.assignResponsibilities(invitation.getEvent(), invitation.getInvitee(), invitation.getResponsibilities());
        invitation.setStatus(CoOrganizerInvitationStatus.ACCEPTED);
        invitation = coOrganizerInvitationRepository.save(invitation);

        eventLogService.appendLog(
                invitation.getEvent(),
                invitation.getInvitee(),
                null,
                EventActionType.CO_ORGANIZER_INVITATION_ACCEPTED,
                CoOrganizerInvitationStatus.PENDING.name(),
                CoOrganizerInvitationStatus.ACCEPTED.name(),
                "El usuario aceptó la invitación con responsabilidades: " + formatResponsibilities(invitation.getResponsibilities())
        );

        return toResponse(invitation);
    }

    @Transactional
    public CoOrganizerInvitationResponse rejectInvitation(Long invitationId) {
        CoOrganizerInvitation invitation = findMine(invitationId);
        ensurePending(invitation);

        invitation.setStatus(CoOrganizerInvitationStatus.REJECTED);
        invitation = coOrganizerInvitationRepository.save(invitation);

        eventLogService.appendLog(
                invitation.getEvent(),
                invitation.getInvitee(),
                null,
                EventActionType.CO_ORGANIZER_INVITATION_REJECTED,
                CoOrganizerInvitationStatus.PENDING.name(),
                CoOrganizerInvitationStatus.REJECTED.name(),
                "El usuario rechazó la invitación al evento."
        );

        return toResponse(invitation);
    }

    @Transactional
    public void revokeInvitation(Long eventId, Long invitationId) {
        Event event = findManageableEvent(eventId);
        CoOrganizerInvitation invitation = coOrganizerInvitationRepository.findByIdAndEventId(invitationId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la invitación solicitada para este evento."));
        ensurePending(invitation);

        invitation.setStatus(CoOrganizerInvitationStatus.REVOKED);
        coOrganizerInvitationRepository.save(invitation);

        eventLogService.appendOrganizerLog(
                event,
                EventActionType.CO_ORGANIZER_INVITATION_REVOKED,
                CoOrganizerInvitationStatus.PENDING.name(),
                CoOrganizerInvitationStatus.REVOKED.name(),
                "Invitación revocada para el usuario " + invitation.getInvitee().getUsername()
        );
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private Event findManageableEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        if (event.getStatus() == com.tfg.gonzalo.backend.model.enums.EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya ha finalizado y no admite cambios de co-organización.");
        }
        return event;
    }

    private AppUser findAuthenticatedUser() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (principal.userId() == null) {
            throw new ForbiddenOperationException("No se pudo identificar al usuario autenticado.");
        }

        return appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario autenticado."));
    }

    private CoOrganizerInvitation findMine(Long invitationId) {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (principal.userId() == null) {
            throw new ForbiddenOperationException("No se pudo identificar al usuario autenticado.");
        }

        return coOrganizerInvitationRepository.findByIdAndInviteeId(invitationId, principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No existe ninguna invitación pendiente asociada a tu cuenta con ese identificador."));
    }

    private void validateInvitee(Event event, AppUser inviter, AppUser invitee) {
        if (!invitee.isEnabled()) {
            throw new BusinessRuleException("La cuenta invitada no está habilitada.");
        }
        if (invitee.getRole() != GlobalRole.ORGANIZER) {
            throw new BusinessRuleException("Solo se puede invitar a cuentas registradas del panel de administración.");
        }
        if (invitee.getId().equals(event.getOrganizer().getId())) {
            throw new BusinessRuleException("El organizador principal ya pertenece al evento y no necesita invitación.");
        }
        if (invitee.getId().equals(inviter.getId())) {
            throw new BusinessRuleException("No puedes enviarte una invitación a ti mismo.");
        }
        if (!coOrganizerAssignmentRepository.findByEventIdAndUserId(event.getId(), invitee.getId()).isEmpty()) {
            throw new BusinessRuleException("El usuario indicado ya está vinculado al evento como co-organizador.");
        }
        if (coOrganizerInvitationRepository.existsByEventIdAndInviteeIdAndStatus(
                event.getId(),
                invitee.getId(),
                CoOrganizerInvitationStatus.PENDING
        )) {
            throw new BusinessRuleException("Ya existe una invitación pendiente para ese usuario en este evento.");
        }
    }

    private void ensurePendingInvitationSlotAvailable(Long eventId) {
        long activeCoOrganizers = eventRoleService.countActiveCoOrganizers(eventId);
        long pendingInvitations = coOrganizerInvitationRepository.countByEventIdAndStatus(
                eventId,
                CoOrganizerInvitationStatus.PENDING
        );
        if (activeCoOrganizers + pendingInvitations >= EventRoleService.MAX_CO_ORGANIZERS) {
            throw new BusinessRuleException("No quedan plazas disponibles para nuevos co-organizadores en este evento.");
        }
    }

    private void ensurePending(CoOrganizerInvitation invitation) {
        if (invitation.getStatus() != CoOrganizerInvitationStatus.PENDING) {
            throw new BusinessRuleException("La invitación ya no se encuentra pendiente.");
        }
    }

    private Set<CoOrganizerResponsibility> normalizeResponsibilities(Set<CoOrganizerResponsibility> responsibilities) {
        return responsibilities.stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String formatResponsibilities(Set<CoOrganizerResponsibility> responsibilities) {
        return responsibilities.stream()
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private CoOrganizerInvitationResponse toResponse(CoOrganizerInvitation invitation) {
        Set<CoOrganizerResponsibility> responsibilities = normalizeResponsibilities(invitation.getResponsibilities());
        return new CoOrganizerInvitationResponse(
                invitation.getId(),
                invitation.getEvent().getId(),
                invitation.getEvent().getName(),
                invitation.getEvent().getStatus(),
                invitation.getEvent().getTimeLimitMinutes(),
                invitation.getEvent().getOrganizer().getUsername(),
                invitation.getEvent().getOrganizer().getDisplayName(),
                invitation.getInvitee().getId(),
                invitation.getInvitee().getUsername(),
                invitation.getInvitee().getDisplayName(),
                invitation.getInvitee().getEmail(),
                invitation.getStatus(),
                responsibilities,
                invitation.getCreatedAt()
        );
    }
}