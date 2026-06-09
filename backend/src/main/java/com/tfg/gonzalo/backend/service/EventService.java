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
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private static final int DEFAULT_TIME_LIMIT_MINUTES = 180;
    private final AppUserRepository appUserRepository;
    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;
    private final EventRepository eventRepository;
    private final EventGroupRepository eventGroupRepository;
    private final EventLogRepository eventLogRepository;
    private final EventLogService eventLogService;
    private final CodeGeneratorService codeGeneratorService;
    private final VillainService villainService;
    private final InterPodCardDefinitionService interPodCardDefinitionService;
    private final VillainFormulaService villainFormulaService;
    private final InterPodCardService interPodCardService;
    private final EventPlayerMetricsService eventPlayerMetricsService;
    private final AccessControlService accessControlService;
    private final InterPodCardImageStorageService interPodCardImageStorageService;

    public EventService(AppUserRepository appUserRepository,
                        CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                        EventRepository eventRepository,
                        EventGroupRepository eventGroupRepository,
                        EventLogRepository eventLogRepository,
                        EventLogService eventLogService,
                        CodeGeneratorService codeGeneratorService,
                        VillainService villainService,
                        InterPodCardDefinitionService interPodCardDefinitionService,
                        VillainFormulaService villainFormulaService,
                        InterPodCardService interPodCardService,
                        EventPlayerMetricsService eventPlayerMetricsService,
                        AccessControlService accessControlService,
                        InterPodCardImageStorageService interPodCardImageStorageService) {
        this.appUserRepository = appUserRepository;
                this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.eventRepository = eventRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
        this.codeGeneratorService = codeGeneratorService;
        this.villainService = villainService;
        this.interPodCardDefinitionService = interPodCardDefinitionService;
        this.villainFormulaService = villainFormulaService;
        this.interPodCardService = interPodCardService;
        this.eventPlayerMetricsService = eventPlayerMetricsService;
        this.accessControlService = accessControlService;
        this.interPodCardImageStorageService = interPodCardImageStorageService;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        AppUser organizer = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario autenticado."));
        VillainDefinition selectedVillain = villainService
            .findSelectableVillainForOrganizer(organizer.getId(), request.villainDefinitionId());

        Event event = new Event();
        event.setName(request.name().trim());
        event.setConfiguredPlayerCount(0);
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        event.setStatus(EventStatus.DRAFT);
        event.setEventCode(generateUniqueEventCode());
        event.setOrganizer(organizer);
        event.setVillain(buildEventVillain(selectedVillain));
        initializeRuntimeState(event);
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

        return events.stream().map(event -> {
            eventPlayerMetricsService.synchronizePlayerMetrics(event);
            return toResponse(event, principal);
        }).toList();
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
        recalculateThreatTarget(event);
        reconcileTimerConfiguration(event);
        event = eventRepository.save(event);
        eventPlayerMetricsService.synchronizePlayerMetrics(event);
        event = findEvent(eventId);

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
        eventPlayerMetricsService.synchronizePlayerMetrics(event);
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

    private void initializeRuntimeState(Event event) {
        int villainHpMax = villainFormulaService.computeVillainHp(
                event.getVillain().getHpRuleType(),
                event.getVillain().getHpRuleValue(),
                event.getConfiguredPlayerCount()
        );
        event.setVillainHpMax(villainHpMax);
        event.setVillainHpCurrent(villainHpMax);
        recalculateThreatTarget(event);
        event.setSchemeThreatCurrent(0);
        event.setTimerTotalSeconds(event.getTimeLimitMinutes() * 60);
        event.setTimerRemainingSeconds(event.getTimerTotalSeconds());
        event.setTimerStatus(event.getTimerTotalSeconds() > 0 ? TimerStatus.READY : TimerStatus.NOT_CONFIGURED);
        event.setFinishCause(null);
        event.setTotalReportedVillainDamage(0);
        event.setVillainHalfHpNotified(false);
        event.setVillainQuarterHpNotified(false);
        event.setThreatHalfNotified(false);
        event.setThreatThreeQuarterNotified(false);
        event.setTimerThirtyMinutesNotified(false);
        event.setTimerFifteenMinutesNotified(false);
        event.setTimerFiveMinutesNotified(false);
        event.setTimerStartedAt(null);
        event.setTimerPausedAt(null);
    }

    private void recalculateThreatTarget(Event event) {
        int currentGroupCount = eventGroupRepository.findByEventId(event.getId() == null ? -1L : event.getId()).size();
        event.setSchemeThreatTarget(villainFormulaService.computeThreatTarget(
                event.getVillain().getThreatRuleType(),
                event.getVillain().getThreatRuleValue(),
                currentGroupCount
        ));
    }

    private void reconcileTimerConfiguration(Event event) {
        event.setTimerTotalSeconds(event.getTimeLimitMinutes() * 60);
        event.setTimerRemainingSeconds(Math.min(event.getTimerRemainingSeconds(), event.getTimerTotalSeconds()));
        if (event.getTimerTotalSeconds() <= 0) {
            event.setTimerStatus(TimerStatus.NOT_CONFIGURED);
            event.setTimerStartedAt(null);
            event.setTimerPausedAt(null);
            event.setTimerRemainingSeconds(0);
        } else if (event.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            event.setTimerStatus(TimerStatus.READY);
            event.setTimerRemainingSeconds(event.getTimerTotalSeconds());
        }
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
            .listDefinitionsVisibleToOrganizer(event.getOrganizer().getId()).stream()
            .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
            .map(this::toInterPodCardDefinitionResponse)
            .toList();

        boolean currentUserIsOwner = principal != null
            && principal.userId() != null
            && principal.userId().equals(event.getOrganizer().getId());
        Set<CoOrganizerResponsibility> currentUserResponsibilities = resolveCurrentUserResponsibilities(event, principal);

        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getConfiguredPlayerCount(),
                event.getTimeLimitMinutes(),
                event.getStatus(),
                event.getResult(),
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
                event.getVillainHpCurrent(),
                event.getVillainHpMax(),
                event.getSchemeThreatCurrent(),
                event.getSchemeThreatTarget(),
                event.getTimerRemainingSeconds(),
                event.getTimerStatus(),
                currentUserIsOwner,
                currentUserResponsibilities,
                event.getStartedAt(),
                event.getFinishedAt(),
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
                definition.getDefaultHpMax(),
            definition.getDefaultThreatMax(),
                definition.getDefaultStatus(),
                interPodCardImageStorageService.resolveDefinitionImageUrl(definition.getId(), definition.getImagePath()),
                definition.getOwner() == null ? null : definition.getOwner().getId(),
                definition.getOwner() == null ? null : definition.getOwner().getUsername(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }
}