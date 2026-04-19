package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CreateEventRequest;
import com.tfg.gonzalo.backend.dto.event.EventResponse;
import com.tfg.gonzalo.backend.dto.event.UpdateEventRequest;
import com.tfg.gonzalo.backend.dto.interpod.InterPodCardDefinitionResponse;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.CoOrganizerAssignment;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.InterPodCardDefinition;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerAssignmentRepository;
import com.tfg.gonzalo.backend.repository.CoOrganizerInvitationRepository;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
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
    private final CoOrganizerAssignmentRepository coOrganizerAssignmentRepository;
    private final CoOrganizerInvitationRepository coOrganizerInvitationRepository;
    private final EventRepository eventRepository;
    private final EventGroupRepository eventGroupRepository;
    private final GameStateRepository gameStateRepository;
    private final EventLogRepository eventLogRepository;
    private final EventLogService eventLogService;
    private final CodeGeneratorService codeGeneratorService;
    private final VillainService villainService;
    private final InterPodCardDefinitionService interPodCardDefinitionService;
    private final VillainFormulaService villainFormulaService;
    private final InterPodCardService interPodCardService;
    private final EventPlayerMetricsService eventPlayerMetricsService;
    private final AccessControlService accessControlService;

    public EventService(AppUserRepository appUserRepository,
                        CoOrganizerAssignmentRepository coOrganizerAssignmentRepository,
                        CoOrganizerInvitationRepository coOrganizerInvitationRepository,
                        EventRepository eventRepository,
                        EventGroupRepository eventGroupRepository,
                        GameStateRepository gameStateRepository,
                        EventLogRepository eventLogRepository,
                        EventLogService eventLogService,
                        CodeGeneratorService codeGeneratorService,
                        VillainService villainService,
                        InterPodCardDefinitionService interPodCardDefinitionService,
                        VillainFormulaService villainFormulaService,
                        InterPodCardService interPodCardService,
                        EventPlayerMetricsService eventPlayerMetricsService,
                        AccessControlService accessControlService) {
        this.appUserRepository = appUserRepository;
                this.coOrganizerAssignmentRepository = coOrganizerAssignmentRepository;
                this.coOrganizerInvitationRepository = coOrganizerInvitationRepository;
        this.eventRepository = eventRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.gameStateRepository = gameStateRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
        this.codeGeneratorService = codeGeneratorService;
        this.villainService = villainService;
        this.interPodCardDefinitionService = interPodCardDefinitionService;
        this.villainFormulaService = villainFormulaService;
        this.interPodCardService = interPodCardService;
        this.eventPlayerMetricsService = eventPlayerMetricsService;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        AppUser organizer = appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario autenticado."));
        VillainDefinition selectedVillain = villainService
            .findSelectableVillainForOrganizer(organizer.getId(), request.villainDefinitionId());
        List<InterPodCardDefinition> selectedInterPodCardDefinitions = interPodCardDefinitionService
            .resolveSelectedDefinitionsForOrganizer(organizer.getId(), request.interPodCardDefinitionIds());

        Event event = new Event();
        event.setName(request.name().trim());
        event.setConfiguredPlayerCount(0);
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        event.setStatus(EventStatus.DRAFT);
        event.setEventCode(generateUniqueEventCode());
        event.setOrganizer(organizer);
        event.setVillainDefinition(selectedVillain);
        event.setInterPodCardDefinitions(new LinkedHashSet<>(selectedInterPodCardDefinitions));
        applyVillainSnapshot(event, selectedVillain);
        event = eventRepository.save(event);

        GameState gameState = new GameState();
        gameState.setEvent(event);
        gameState.setVillainHpMax(villainFormulaService.computeVillainHp(
            event.getVillainHpRuleTypeSnapshot(),
            event.getVillainHpRuleValueSnapshot(),
            event.getConfiguredPlayerCount()
        ));
        gameState.setVillainHpCurrent(gameState.getVillainHpMax());
        gameState.setSchemeThreatTarget(villainFormulaService.computeThreatTarget(
            event.getVillainThreatRuleTypeSnapshot(),
            event.getVillainThreatRuleValueSnapshot(),
            0
        ));
        gameState.setSchemeThreatCurrent(0);
        gameState.setTimerTotalSeconds(event.getTimeLimitMinutes() * 60);
        gameState.setTimerRemainingSeconds(gameState.getTimerTotalSeconds());
        gameState.setTimerStatus(gameState.getTimerTotalSeconds() > 0 ? TimerStatus.READY : TimerStatus.NOT_CONFIGURED);
        gameState = gameStateRepository.save(gameState);

        eventLogService.appendLog(event, organizer, null, EventActionType.EVENT_CREATED,
            null, event.getName(), "Evento creado con estado global inicial.");

        return toResponse(event, gameState, principal);
    }

    @Transactional
    public List<EventResponse> getMyEvents() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        if (principal.userId() == null) {
            return List.of();
        }

        List<Event> events = java.util.stream.Stream.concat(
                        eventRepository.findByOrganizerIdOrderByCreatedAtDesc(principal.userId()).stream(),
                        coOrganizerAssignmentRepository.findByUserId(principal.userId()).stream()
                                .map(CoOrganizerAssignment::getEvent)
                )
            .collect(Collectors.toMap(Event::getId, event -> event, (left, rightEvent) -> left))
                .values()
                .stream()
                .sorted(Comparator.comparing(Event::getCreatedAt).reversed())
                .toList();

        return events.stream().map(event -> {
            eventPlayerMetricsService.synchronizePlayerMetrics(event);
            GameState gameState = gameStateRepository.findByEventId(event.getId()).orElse(null);
            return toResponse(event, gameState, principal);
        }).toList();
    }

    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        ensureEditable(event);
        Long currentVillainDefinitionId = event.getVillainDefinition() != null
            ? event.getVillainDefinition().getId()
            : null;
        Long villainDefinitionId = request.villainDefinitionId() == null
            ? currentVillainDefinitionId
            : request.villainDefinitionId();
        VillainDefinition selectedVillain = villainService
            .findSelectableVillainForOrganizer(event.getOrganizer().getId(), villainDefinitionId);

        event.setName(request.name().trim());
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        event.setVillainDefinition(selectedVillain);
        applyVillainSnapshot(event, selectedVillain);
        event = eventRepository.save(event);

        GameState gameState = findGameState(eventId);
        int currentGroupCount = eventGroupRepository.findByEventId(eventId).size();
        gameState.setSchemeThreatTarget(villainFormulaService.computeThreatTarget(
            event.getVillainThreatRuleTypeSnapshot(),
            event.getVillainThreatRuleValueSnapshot(),
            currentGroupCount
        ));
        gameState.setTimerTotalSeconds(event.getTimeLimitMinutes() * 60);
        gameState.setTimerRemainingSeconds(Math.min(gameState.getTimerRemainingSeconds(), gameState.getTimerTotalSeconds()));
        if (gameState.getTimerTotalSeconds() <= 0) {
            gameState.setTimerStatus(TimerStatus.NOT_CONFIGURED);
            gameState.setTimerStartedAt(null);
            gameState.setTimerPausedAt(null);
            gameState.setTimerRemainingSeconds(0);
        } else if (gameState.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            gameState.setTimerStatus(TimerStatus.READY);
            gameState.setTimerRemainingSeconds(gameState.getTimerTotalSeconds());
        }
        gameStateRepository.save(gameState);
        eventPlayerMetricsService.synchronizePlayerMetrics(event);
        gameState = findGameState(eventId);

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_UPDATED,
            null, event.getName(), "Evento actualizado antes del inicio.");

        return toResponse(event, gameState, accessControlService.requireAuthenticatedPrincipal());
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        ensureEditable(event);
        coOrganizerInvitationRepository.deleteByEventId(eventId);
        coOrganizerAssignmentRepository.deleteByEventId(eventId);
        interPodCardService.deleteCardsForEvent(eventId);
        gameStateRepository.findByEventId(eventId).ifPresent(gameStateRepository::delete);
        eventLogRepository.deleteAll(eventLogRepository.findByEventIdOrderByCreatedAtDesc(eventId));
        eventRepository.delete(event);
    }

    @Transactional
    public EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);
        eventPlayerMetricsService.synchronizePlayerMetrics(event);
        GameState gameState = findGameState(eventId);
        return toResponse(event, gameState, accessControlService.requireAuthenticatedPrincipal());
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private GameState findGameState(Long eventId) {
        return gameStateRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe estado global para el evento solicitado."));
    }

    private void ensureEditable(Event event) {
        if (event.getStatus() == EventStatus.IN_PROGRESS || event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("El evento ya ha comenzado y no puede modificarse desde este caso de uso.");
        }
    }

    private Integer resolveTimeLimit(Integer timeLimitMinutes) {
        return timeLimitMinutes == null ? DEFAULT_TIME_LIMIT_MINUTES : timeLimitMinutes;
    }

    private void applyVillainSnapshot(Event event, VillainDefinition villainDefinition) {
        event.setVillainNameSnapshot(villainDefinition.getName());
        event.setVillainTypeSnapshot(villainDefinition.getType());
        event.setVillainHpRuleTypeSnapshot(villainDefinition.getHpRuleType());
        event.setVillainHpRuleValueSnapshot(villainDefinition.getHpRuleValue());
        event.setVillainThreatRuleTypeSnapshot(villainDefinition.getThreatRuleType());
        event.setVillainThreatRuleValueSnapshot(villainDefinition.getThreatRuleValue());
    }

    private String generateUniqueEventCode() {
        String eventCode = codeGeneratorService.generateEventCode();
        while (eventRepository.existsByEventCode(eventCode)) {
            eventCode = codeGeneratorService.generateEventCode();
        }
        return eventCode;
    }

    private EventResponse toResponse(Event event, GameState gameState, AuthPrincipal principal) {
        Long villainDefinitionId = event.getVillainDefinition() != null
                ? event.getVillainDefinition().getId()
                : null;
        List<InterPodCardDefinitionResponse> selectedInterPodCardDefinitions = event.getInterPodCardDefinitions()
            .stream()
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
                event.getVillainNameSnapshot(),
                event.getVillainTypeSnapshot(),
                selectedInterPodCardDefinitions,
                event.getVillainHpRuleTypeSnapshot(),
                event.getVillainHpRuleValueSnapshot(),
                event.getVillainThreatRuleTypeSnapshot(),
                event.getVillainThreatRuleValueSnapshot(),
                gameState != null ? gameState.getVillainHpCurrent() : null,
                gameState != null ? gameState.getVillainHpMax() : null,
                gameState != null ? gameState.getSchemeThreatCurrent() : null,
                gameState != null ? gameState.getSchemeThreatTarget() : null,
                gameState != null ? gameState.getTimerRemainingSeconds() : null,
                gameState != null ? gameState.getTimerStatus() : null,
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
        return coOrganizerAssignmentRepository.findByEventIdAndUserId(event.getId(), principal.userId()).stream()
                .map(CoOrganizerAssignment::getResponsibility)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private InterPodCardDefinitionResponse toInterPodCardDefinitionResponse(InterPodCardDefinition definition) {
        String imageUrl = definition.getImagePath() != null
            ? "/api/inter-pod-card-definitions/" + definition.getId() + "/image"
                : null;
        return new InterPodCardDefinitionResponse(
                definition.getId(),
                definition.getName(),
                definition.getType(),
                definition.getCardCategory(),
                definition.getDefaultHpMax(),
                definition.getDefaultStatus(),
                imageUrl,
                definition.getOwner() == null ? null : definition.getOwner().getId(),
                definition.getOwner() == null ? null : definition.getOwner().getUsername(),
                definition.getCreatedAt(),
                definition.getUpdatedAt()
        );
    }
}