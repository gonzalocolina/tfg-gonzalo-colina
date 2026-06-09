package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.AssignCoOrganizerRequest;
import com.tfg.gonzalo.backend.dto.event.CoOrganizerResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerInvitation;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventRoleService {

    static final int MAX_CO_ORGANIZERS = 3;

    private final EventRepository eventRepository;
    private final AppUserRepository appUserRepository;
    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;
    private final EventLogService eventLogService;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;

    public EventRoleService(EventRepository eventRepository,
                            AppUserRepository appUserRepository,
                            CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                            EventLogService eventLogService,
                            PasswordEncoder passwordEncoder,
                            AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.appUserRepository = appUserRepository;
        this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.eventLogService = eventLogService;
        this.passwordEncoder = passwordEncoder;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public CoOrganizerResponse assignCoOrganizer(Long eventId, AssignCoOrganizerRequest request) {
        Event event = findEditableEvent(eventId);
        String normalizedUsername = normalizeUsername(request.username());

        AppUser user = appUserRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseGet(() -> createCoOrganizerUser(request));

        if (user.getRole() != GlobalRole.CO_ORGANIZER) {
            throw new BusinessRuleException("El usuario ya existe con un rol incompatible.");
        }

        CoOrganizerInvitation coOrganizer = activateCoOrganizer(event, user, null, request.responsibilities());

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_UPDATED,
                null,
                user.getUsername(),
                "Co-organizador asignado con responsabilidades: " + request.responsibilities());

        return toResponse(coOrganizer);
    }

    @Transactional(readOnly = true)
    public List<CoOrganizerResponse> listCoOrganizers(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);

        return coOrganizerInvitationRepository.findByEventIdAndStatusOrderByCreatedAtDesc(
                        eventId,
                        CoOrganizerInvitationStatus.ACCEPTED
                ).stream()
                .collect(Collectors.toMap(
                        invitation -> invitation.getInvitee().getId(),
                        invitation -> invitation,
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(this::toResponse)
                .sorted((left, right) -> left.username().compareToIgnoreCase(right.username()))
                .toList();
    }

    CoOrganizerInvitation activateCoOrganizer(Event event,
                                              AppUser user,
                                              AppUser invitedBy,
                                              Set<CoOrganizerResponsibility> responsibilities) {
        ensureCoOrganizerSlotAvailable(event.getId(), user.getId());
        Set<CoOrganizerResponsibility> normalizedResponsibilities = normalizeResponsibilities(responsibilities);
        ensureResponsibilitiesAvailable(event, user, normalizedResponsibilities);

        CoOrganizerInvitation coOrganizer = coOrganizerInvitationRepository
                .findByEventIdAndInviteeIdOrderByCreatedAtDesc(event.getId(), user.getId())
                .stream()
                .findFirst()
                .orElseGet(CoOrganizerInvitation::new);

        coOrganizer.setEvent(event);
        coOrganizer.setInvitee(user);
        coOrganizer.setInvitedBy(invitedBy);
        coOrganizer.setStatus(CoOrganizerInvitationStatus.ACCEPTED);
        coOrganizer.setResponsibilities(normalizedResponsibilities);
        return coOrganizerInvitationRepository.save(coOrganizer);
    }

    long countActiveCoOrganizers(Long eventId) {
        return coOrganizerInvitationRepository.findByEventIdAndStatusOrderByCreatedAtDesc(
                        eventId,
                        CoOrganizerInvitationStatus.ACCEPTED
                ).stream()
                .map(invitation -> invitation.getInvitee().getId())
                .distinct()
                .count();
    }

    void ensureCoOrganizerSlotAvailable(Long eventId, Long userId) {
        if (!coOrganizerInvitationRepository.existsByEventIdAndInviteeIdAndStatus(
                eventId,
                userId,
                CoOrganizerInvitationStatus.ACCEPTED
        ) && countActiveCoOrganizers(eventId) >= MAX_CO_ORGANIZERS) {
            throw new BusinessRuleException("No se pueden asignar más de 3 co-organizadores por evento.");
        }
    }

    void removeCoOrganizer(Event event, AppUser user) {
        if (event.getStatus() == EventStatus.IN_PROGRESS || event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("Solo se puede revocar un co-organizador antes de iniciar la partida.");
        }

        List<CoOrganizerInvitation> activeCoOrganizers = coOrganizerInvitationRepository
                .findByEventIdAndInviteeIdOrderByCreatedAtDesc(event.getId(), user.getId())
                .stream()
                .filter(invitation -> invitation.getStatus() == CoOrganizerInvitationStatus.ACCEPTED)
                .toList();
        if (activeCoOrganizers.isEmpty()) {
            throw new BusinessRuleException("El usuario indicado ya no está vinculado al evento como co-organizador.");
        }

        activeCoOrganizers.forEach(invitation -> invitation.setStatus(CoOrganizerInvitationStatus.REVOKED));
        coOrganizerInvitationRepository.saveAll(activeCoOrganizers);
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private Event findEditableEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        if (event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya ha finalizado y no admite cambios de roles.");
        }
        return event;
    }

    private AppUser createCoOrganizerUser(AssignCoOrganizerRequest request) {
        String normalizedEmail = normalizeOptionalEmail(request.email());
        if (normalizedEmail != null && appUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessRuleException("Ya existe una cuenta con ese email.");
        }

        AppUser user = new AppUser();
        user.setUsername(normalizeUsername(request.username()));
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(GlobalRole.CO_ORGANIZER);
        user.setEnabled(true);
        return appUserRepository.save(user);
    }

    private void ensureResponsibilitiesAvailable(Event event,
                                                 AppUser user,
                                                 Set<CoOrganizerResponsibility> responsibilities) {
        coOrganizerInvitationRepository.findByEventIdAndStatusOrderByCreatedAtDesc(
                        event.getId(),
                        CoOrganizerInvitationStatus.ACCEPTED
                ).stream()
                .filter(invitation -> !invitation.getInvitee().getId().equals(user.getId()))
                .forEach(invitation -> responsibilities.forEach(responsibility -> {
                    if (invitation.getResponsibilities().contains(responsibility)) {
                        throw new BusinessRuleException("La responsabilidad " + responsibility
                                + " ya está asignada a otro co-organizador.");
                    }
                }));
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Set<CoOrganizerResponsibility> normalizeResponsibilities(Set<CoOrganizerResponsibility> responsibilities) {
        return responsibilities.stream()
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private CoOrganizerResponse toResponse(CoOrganizerInvitation coOrganizer) {
        return new CoOrganizerResponse(
                coOrganizer.getInvitee().getId(),
                coOrganizer.getInvitee().getUsername(),
                coOrganizer.getInvitee().getEmail(),
                normalizeResponsibilities(coOrganizer.getResponsibilities()),
                coOrganizer.getInvitedBy() != null ? coOrganizer.getId() : null
        );
    }

}
