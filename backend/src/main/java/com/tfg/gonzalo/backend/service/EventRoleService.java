package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.AssignCoOrganizerRequest;
import com.tfg.gonzalo.backend.dto.event.CoOrganizerResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerAssignment;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerAssignmentRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final CoOrganizerAssignmentRepository coOrganizerAssignmentRepository;
    private final EventLogService eventLogService;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;

    public EventRoleService(EventRepository eventRepository,
                            AppUserRepository appUserRepository,
                            CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                            CoOrganizerAssignmentRepository coOrganizerAssignmentRepository,
                            EventLogService eventLogService,
                            PasswordEncoder passwordEncoder,
                            AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.appUserRepository = appUserRepository;
        this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.coOrganizerAssignmentRepository = coOrganizerAssignmentRepository;
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

        ensureCoOrganizerSlotAvailable(eventId, user.getId());
        assignResponsibilities(event, user, request.responsibilities());

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_UPDATED,
            null,
            user.getUsername(),
            "Co-organizador asignado con responsabilidades: " + request.responsibilities());

        return toResponse(
            user,
            coOrganizerAssignmentRepository.findByEventIdAndUserId(eventId, user.getId()),
            null
        );
    }

    @Transactional(readOnly = true)
    public List<CoOrganizerResponse> listCoOrganizers(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);

        Map<Long, Long> acceptedInvitationIds = coOrganizerInvitationRepository
            .findByEventIdAndStatusOrderByCreatedAtDesc(eventId, CoOrganizerInvitationStatus.ACCEPTED)
            .stream()
            .collect(Collectors.toMap(
                invitation -> invitation.getInvitee().getId(),
                com.tfg.gonzalo.backend.model.CoOrganizerInvitation::getId,
                (existing, ignored) -> existing
            ));

        return coOrganizerAssignmentRepository.findByEventId(eventId).stream()
            .collect(Collectors.groupingBy(assignment -> assignment.getUser().getId()))
                .entrySet()
                .stream()
            .map(entry -> toResponse(
                entry.getValue().get(0).getUser(),
                entry.getValue(),
                acceptedInvitationIds.get(entry.getKey())
            ))
                .sorted((left, right) -> left.username().compareToIgnoreCase(right.username()))
                .toList();
    }

    void assignResponsibilities(Event event, AppUser user, Set<CoOrganizerResponsibility> responsibilities) {
        Long eventId = event.getId();
        List<CoOrganizerAssignment> existingAssignments = coOrganizerAssignmentRepository
                .findByEventIdAndUserId(eventId, user.getId());
        Set<CoOrganizerResponsibility> existingResponsibilities = existingAssignments.stream()
                .map(CoOrganizerAssignment::getResponsibility)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (CoOrganizerResponsibility responsibility : responsibilities) {
            coOrganizerAssignmentRepository.findByEventIdAndResponsibility(eventId, responsibility)
                    .ifPresent(assignment -> {
                        if (!assignment.getUser().getId().equals(user.getId())) {
                            throw new BusinessRuleException("La responsabilidad " + responsibility +
                                    " ya está asignada a otro co-organizador.");
                        }
                    });

            if (!existingResponsibilities.contains(responsibility)) {
                CoOrganizerAssignment assignment = new CoOrganizerAssignment();
                assignment.setEvent(event);
                assignment.setUser(user);
                assignment.setResponsibility(responsibility);
                coOrganizerAssignmentRepository.save(assignment);
            }
        }
    }

    long countActiveCoOrganizers(Long eventId) {
        return coOrganizerAssignmentRepository.findByEventId(eventId).stream()
                .map(assignment -> assignment.getUser().getId())
                .distinct()
                .count();
    }

    void ensureCoOrganizerSlotAvailable(Long eventId, Long userId) {
        if (coOrganizerAssignmentRepository.findByEventIdAndUserId(eventId, userId).isEmpty()
                && countActiveCoOrganizers(eventId) >= MAX_CO_ORGANIZERS) {
            throw new BusinessRuleException("No se pueden asignar más de 3 co-organizadores por evento.");
        }
    }

    void removeCoOrganizer(Event event, AppUser user) {
        if (event.getStatus() == EventStatus.IN_PROGRESS || event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("Solo se puede revocar un co-organizador antes de iniciar la partida.");
        }

        List<CoOrganizerAssignment> assignments = coOrganizerAssignmentRepository
                .findByEventIdAndUserId(event.getId(), user.getId());
        if (assignments.isEmpty()) {
            throw new BusinessRuleException("El usuario indicado ya no está vinculado al evento como co-organizador.");
        }

        coOrganizerAssignmentRepository.deleteAll(assignments);
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

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private CoOrganizerResponse toResponse(AppUser user,
                                           List<CoOrganizerAssignment> assignments,
                                           Long invitationId) {
        Set<CoOrganizerResponsibility> responsibilities = assignments.stream()
                .map(CoOrganizerAssignment::getResponsibility)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new CoOrganizerResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                responsibilities,
                invitationId
        );
    }

}
