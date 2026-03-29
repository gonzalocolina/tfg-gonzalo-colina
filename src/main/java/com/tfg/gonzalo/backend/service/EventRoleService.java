package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.AssignCoOrganizerRequest;
import com.tfg.gonzalo.backend.dto.event.CoOrganizerResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerAssignment;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GlobalRole;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerAssignmentRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EventRoleService {

    private static final int MAX_CO_ORGANIZERS = 3;

    private final EventRepository eventRepository;
    private final AppUserRepository appUserRepository;
    private final CoOrganizerAssignmentRepository coOrganizerAssignmentRepository;
    private final EventLogService eventLogService;
    private final PasswordEncoder passwordEncoder;
    private final AccessControlService accessControlService;

    public EventRoleService(EventRepository eventRepository,
                            AppUserRepository appUserRepository,
                            CoOrganizerAssignmentRepository coOrganizerAssignmentRepository,
                            EventLogService eventLogService,
                            PasswordEncoder passwordEncoder,
                            AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.appUserRepository = appUserRepository;
        this.coOrganizerAssignmentRepository = coOrganizerAssignmentRepository;
        this.eventLogService = eventLogService;
        this.passwordEncoder = passwordEncoder;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public CoOrganizerResponse assignCoOrganizer(Long eventId, AssignCoOrganizerRequest request) {
        Event event = findEditableEvent(eventId);
        if (coOrganizerAssignmentRepository.countByEventId(eventId) >= MAX_CO_ORGANIZERS &&
                coOrganizerAssignmentRepository.findByEventId(eventId).stream()
                        .map(assignment -> assignment.getUser().getId())
                        .distinct()
                        .count() >= MAX_CO_ORGANIZERS) {
            throw new BusinessRuleException("No se pueden asignar más de 3 co-organizadores por evento.");
        }

        AppUser user = appUserRepository.findByUsername(request.username().trim())
                .orElseGet(() -> createCoOrganizerUser(request));

        if (user.getRole() != GlobalRole.CO_ORGANIZER) {
            throw new BusinessRuleException("El usuario ya existe con un rol incompatible.");
        }

        List<CoOrganizerAssignment> existingAssignments = coOrganizerAssignmentRepository
                .findByEventIdAndUserId(eventId, user.getId());
        Set<CoOrganizerResponsibility> existingResponsibilities = existingAssignments.stream()
                .map(CoOrganizerAssignment::getResponsibility)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (CoOrganizerResponsibility responsibility : request.responsibilities()) {
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

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_UPDATED,
            null,
            user.getUsername(),
            "Co-organizador asignado con responsabilidades: " + request.responsibilities());

        return toResponse(user, coOrganizerAssignmentRepository.findByEventIdAndUserId(eventId, user.getId()));
    }

    public List<CoOrganizerResponse> listCoOrganizers(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);

        return coOrganizerAssignmentRepository.findByEventId(eventId).stream()
            .collect(Collectors.groupingBy(assignment -> assignment.getUser().getId()))
                .entrySet()
                .stream()
            .map(entry -> toResponse(entry.getValue().get(0).getUser(), entry.getValue()))
                .sorted((left, right) -> left.username().compareToIgnoreCase(right.username()))
                .toList();
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private Event findEditableEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        if (event.getStatus() == EventStatus.IN_PROGRESS || event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya no admite cambios de roles.");
        }
        return event;
    }

    private AppUser createCoOrganizerUser(AssignCoOrganizerRequest request) {
        AppUser user = new AppUser();
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeOptional(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(GlobalRole.CO_ORGANIZER);
        user.setEnabled(true);
        return appUserRepository.save(user);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CoOrganizerResponse toResponse(AppUser user, List<CoOrganizerAssignment> assignments) {
        Set<CoOrganizerResponsibility> responsibilities = assignments.stream()
                .map(CoOrganizerAssignment::getResponsibility)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new CoOrganizerResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                responsibilities
        );
    }

}
