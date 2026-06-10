package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CoOrganizerResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerInvitation;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventRoleService {

    static final int MAX_CO_ORGANIZERS = 3;

    private final EventRepository eventRepository;
    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;
    private final AccessControlService accessControlService;

    public EventRoleService(EventRepository eventRepository,
                            CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                            AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.accessControlService = accessControlService;
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
