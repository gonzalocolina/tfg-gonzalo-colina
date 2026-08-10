package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CreateEventRequest;
import com.tfg.gonzalo.backend.dto.event.EventResponse;
import com.tfg.gonzalo.backend.dto.event.UpdateEventRequest;
import com.tfg.gonzalo.backend.dto.interpod.InterPodCardDefinitionResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerInvitation;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventVillain;
import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerInvitationStatus;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import com.tfg.gonzalo.backend.support.DatabaseRetryExecutor;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private static final int DEFAULT_TIME_LIMIT_MINUTES = 180;
    private final AppUserRepository appUserRepository;
    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;
    private final EventRepository eventRepository;
    private final EventLogRepository eventLogRepository;
    private final EventLogService eventLogService;
    private final CodeGeneratorService codeGeneratorService;
    private final VillainService villainService;
    private final InterPodCardDefinitionService interPodCardDefinitionService;
    private final EventStateCalculator eventStateCalculator;
    private final InterPodCardService interPodCardService;
    private final AccessControlService accessControlService;
    private final InterPodCardImageStorageService interPodCardImageStorageService;
    private final DatabaseRetryExecutor databaseRetryExecutor;
    private final TransactionTemplate transactionTemplate;

    public EventService(AppUserRepository appUserRepository,
                        CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                        EventRepository eventRepository,
                        EventLogRepository eventLogRepository,
                        EventLogService eventLogService,
                        CodeGeneratorService codeGeneratorService,
                        VillainService villainService,
                        InterPodCardDefinitionService interPodCardDefinitionService,
                        EventStateCalculator eventStateCalculator,
                        InterPodCardService interPodCardService,
                        AccessControlService accessControlService,
                        InterPodCardImageStorageService interPodCardImageStorageService,
                        DatabaseRetryExecutor databaseRetryExecutor,
                        TransactionTemplate transactionTemplate) {
        this.appUserRepository = appUserRepository;
        this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
        this.codeGeneratorService = codeGeneratorService;
        this.villainService = villainService;
        this.interPodCardDefinitionService = interPodCardDefinitionService;
        this.eventStateCalculator = eventStateCalculator;
        this.interPodCardService = interPodCardService;
        this.accessControlService = accessControlService;
        this.interPodCardImageStorageService = interPodCardImageStorageService;
        this.databaseRetryExecutor = databaseRetryExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    public EventResponse createEvent(CreateEventRequest request) {
        return databaseRetryExecutor.execute(() ->
                transactionTemplate.execute(status -> createEventWithinTransaction(request)));
    }

    private EventResponse createEventWithinTransaction(CreateEventRequest request) {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        AppUser organizer = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario autenticado."));
        VillainDefinition selectedVillain = villainService
            .findSelectableVillainForOrganizer(organizer.getId(), request.villainDefinitionId());

        Event event = new Event();
        event.setName(request.name().trim());
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        event.setEventCode(generateUniqueEventCode());
        event.setOrganizer(organizer);
        event.setVillain(buildEventVillain(selectedVillain));
        initializeRuntimeState(event);
        synchronizeSelectedInterPodCardDefinitions(event, request.interPodCardDefinitionIds(), true);
        event = eventRepository.save(event);

        eventLogService.appendLog(event, organizer, null, EventActionType.EVENT_CREATED,
            null, event.getName(), "Evento creado con estado global inicial.");

        return toResponse(event, principal);
    }

    @Transactional
    public List<EventResponse> getMyEvents() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (principal.userId() == null) {
            return List.of();
        }

        List<Event> events = java.util.stream.Stream.concat(
                        eventRepository.findByOrganizerIdOrderByCreatedAtDesc(principal.userId()).stream(),
                        coOrganizerInvitationRepository.findByInviteeIdAndStatusOrderByCreatedAtDesc(
                                        principal.userId(),
                                        CoOrganizerInvitationStatus.ACCEPTED
                                ).stream()
                                .map(CoOrganizerInvitation::getEvent)
                )
            .collect(Collectors.toMap(Event::getId, event -> event, (left, rightEvent) -> left))
                .values()
                .stream()
                .sorted(Comparator.comparing(Event::getCreatedAt).reversed())
                .toList();

        return events.stream().map(event -> toResponse(event, principal)).toList();
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        ensureEditable(event);

        Long currentVillainDefinitionId = event.getVillain() != null && event.getVillain().getVillainDefinition() != null
            ? event.getVillain().getVillainDefinition().getId()
            : null;
        Long villainDefinitionId = request.villainDefinitionId() == null
            ? currentVillainDefinitionId
            : request.villainDefinitionId();
        VillainDefinition selectedVillain = villainService
            .findSelectableVillainForOrganizer(event.getOrganizer().getId(), villainDefinitionId);

        event.setName(request.name().trim());
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        synchronizeEventVillain(event, selectedVillain);
        if (request.interPodCardDefinitionIds() != null) {
            synchronizeSelectedInterPodCardDefinitions(event, request.interPodCardDefinitionIds(), false);
        }
        resetTimer(event);
        event = eventRepository.save(event);

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_UPDATED,
            null, event.getName(), "Evento actualizado antes del inicio.");

        return toResponse(event, accessControlService.requireAuthenticatedPrincipal());
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        ensureEditable(event);
        coOrganizerInvitationRepository.deleteByEventId(eventId);
        interPodCardService.deleteCardsForEvent(eventId);
        eventLogRepository.deleteAll(eventLogRepository.findByEventIdOrderByCreatedAtDesc(eventId));
        eventRepository.delete(event);
    }

    @Transactional
    public EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);
        return toResponse(event, accessControlService.requireAuthenticatedPrincipal());
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private void ensureEditable(Event event) {
        if (event.getStatus() == EventStatus.IN_PROGRESS || event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya ha comenzado y no puede modificarse desde este caso de uso.");
        }
    }

    private Integer resolveTimeLimit(Integer timeLimitMinutes) {
        return timeLimitMinutes == null ? DEFAULT_TIME_LIMIT_MINUTES : timeLimitMinutes;
    }

    private EventVillain buildEventVillain(VillainDefinition villainDefinition) {
        EventVillain eventVillain = new EventVillain();
        copyVillainDefinition(eventVillain, villainDefinition);
        return eventVillain;
    }

    private void synchronizeEventVillain(Event event, VillainDefinition villainDefinition) {
        EventVillain eventVillain = event.getVillain();
        if (eventVillain == null) {
            eventVillain = new EventVillain();
            event.setVillain(eventVillain);
        }
        copyVillainDefinition(eventVillain, villainDefinition);
    }

    private void copyVillainDefinition(EventVillain eventVillain, VillainDefinition villainDefinition) {
        eventVillain.setVillainDefinition(villainDefinition);
        eventVillain.setName(villainDefinition.getName());
        eventVillain.setType(villainDefinition.getType());
        eventVillain.setHpRuleType(villainDefinition.getHpRuleType());
        eventVillain.setHpRuleValue(villainDefinition.getHpRuleValue());
        eventVillain.setThreatRuleType(villainDefinition.getThreatRuleType());
        eventVillain.setThreatRuleValue(villainDefinition.getThreatRuleValue());
    }

    private void synchronizeSelectedInterPodCardDefinitions(Event event,
                                                            List<Long> requestedDefinitionIds,
                                                            boolean useDefaultWhenEmpty) {
        List<InterPodCardDefinition> selected = interPodCardDefinitionService.resolveSelectedDefinitionsForOrganizer(
                event.getOrganizer().getId(),
                requestedDefinitionIds,
                useDefaultWhenEmpty
        );
        event.getSelectedInterPodCardDefinitions().clear();
        event.getSelectedInterPodCardDefinitions().addAll(selected);
    }

    private void initializeRuntimeState(Event event) {
        event.setVillainDamageTaken(0);
        event.setSchemeThreatCurrent(0);
        event.setTotalReportedVillainDamage(0);
        event.setFinishCause(null);
        event.setStartedAt(null);
        event.setFinishedAt(null);
        resetTimer(event);
    }

    private void resetTimer(Event event) {
        int totalSeconds = event.getTimerTotalSeconds();
        event.setTimerRemainingSeconds(totalSeconds);
        event.setTimerStatus(totalSeconds > 0 ? TimerStatus.READY : TimerStatus.NOT_CONFIGURED);
        event.setTimerStartedAt(null);
    }

    private String generateUniqueEventCode() {
        String eventCode = codeGeneratorService.generateEventCode();
        while (eventRepository.existsByEventCode(eventCode)) {
            eventCode = codeGeneratorService.generateEventCode();
        }
        return eventCode;
    }

    private EventResponse toResponse(Event event, AuthPrincipal principal) {
        Long villainDefinitionId = event.getVillain() != null && event.getVillain().getVillainDefinition() != null
                ? event.getVillain().getVillainDefinition().getId()
                : null;
        List<InterPodCardDefinitionResponse> selectedInterPodCardDefinitions = interPodCardDefinitionService
                .listSelectedDefinitionsForEventEntity(event)
                .stream()
                .sorted(Comparator.comparing(InterPodCardDefinition::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toInterPodCardDefinitionResponse)
                .toList();

        boolean currentUserIsOwner = principal != null
            && principal.userId() != null
            && principal.userId().equals(event.getOrganizer().getId());
        Set<CoOrganizerResponsibility> currentUserResponsibilities = resolveCurrentUserResponsibilities(event, principal);

        return new EventResponse(
                event.getId(),
                event.getName(),
                eventStateCalculator.playerCount(event),
                event.getTimeLimitMinutes(),
                event.getStatus(),
                event.getEventCode(),
                event.getOrganizer().getUsername(),
                villainDefinitionId,
                event.getVillain().getName(),
                event.getVillain().getType(),
                selectedInterPodCardDefinitions,
                event.getVillain().getHpRuleType(),
                event.getVillain().getHpRuleValue(),
                event.getVillain().getThreatRuleType(),
                event.getVillain().getThreatRuleValue(),
                currentUserIsOwner,
                currentUserResponsibilities,
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }

    private Set<CoOrganizerResponsibility> resolveCurrentUserResponsibilities(Event event, AuthPrincipal principal) {
        if (principal == null || principal.userId() == null || principal.userId().equals(event.getOrganizer().getId())) {
            return Set.of();
        }
        return coOrganizerInvitationRepository.findByEventIdAndInviteeIdOrderByCreatedAtDesc(event.getId(), principal.userId())
                .stream()
                .filter(invitation -> invitation.getStatus() == CoOrganizerInvitationStatus.ACCEPTED)
                .flatMap(invitation -> invitation.getResponsibilities().stream())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private InterPodCardDefinitionResponse toInterPodCardDefinitionResponse(InterPodCardDefinition definition) {
        return new InterPodCardDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getType(),
                definition.getCardCategory(),
                definition.getDefaultMetricMax(),
                definition.getRuleType(),
                interPodCardImageStorageService.resolveDefinitionImageUrl(definition.getId(), definition.getImagePath()),
                definition.getOwner() == null ? null : definition.getOwner().getId(),
                definition.getOwner() == null ? null : definition.getOwner().getUsername(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }
}
