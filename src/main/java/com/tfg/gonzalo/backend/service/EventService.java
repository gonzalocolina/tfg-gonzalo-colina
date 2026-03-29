package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CreateEventRequest;
import com.tfg.gonzalo.backend.dto.event.EventResponse;
import com.tfg.gonzalo.backend.dto.event.UpdateEventRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.VillainDefinition;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private static final int DEFAULT_TIME_LIMIT_MINUTES = 180;
    private final AppUserRepository appUserRepository;
    private final EventRepository eventRepository;
    private final EventGroupRepository eventGroupRepository;
    private final GameStateRepository gameStateRepository;
    private final EventLogRepository eventLogRepository;
    private final EventLogService eventLogService;
    private final CodeGeneratorService codeGeneratorService;
    private final VillainService villainService;
    private final VillainFormulaService villainFormulaService;
    private final InterPodCardService interPodCardService;
    private final AccessControlService accessControlService;

    public EventService(AppUserRepository appUserRepository,
                        EventRepository eventRepository,
                        EventGroupRepository eventGroupRepository,
                        GameStateRepository gameStateRepository,
                        EventLogRepository eventLogRepository,
                        EventLogService eventLogService,
                        CodeGeneratorService codeGeneratorService,
                        VillainService villainService,
                        VillainFormulaService villainFormulaService,
                        InterPodCardService interPodCardService,
                        AccessControlService accessControlService) {
        this.appUserRepository = appUserRepository;
        this.eventRepository = eventRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.gameStateRepository = gameStateRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
        this.codeGeneratorService = codeGeneratorService;
        this.villainService = villainService;
        this.villainFormulaService = villainFormulaService;
        this.interPodCardService = interPodCardService;
        this.accessControlService = accessControlService;
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
        event.setConfiguredPlayerCount(request.configuredPlayerCount());
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        event.setStatus(EventStatus.DRAFT);
        event.setEventCode(generateUniqueEventCode());
        event.setOrganizer(organizer);
        event.setVillainDefinition(selectedVillain);
        applyVillainSnapshot(event, selectedVillain);
        event = eventRepository.save(event);

        GameState gameState = new GameState();
        gameState.setEvent(event);
        gameState.setVillainHpMax(villainFormulaService.computeVillainHp(
            event.getVillainHpRuleTypeSnapshot(),
            event.getVillainHpRuleValueSnapshot(),
            request.configuredPlayerCount()
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

        return toResponse(event, gameState);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getMyEvents() {
        AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
        List<Event> events = eventRepository.findByOrganizerIdOrderByCreatedAtDesc(principal.userId());
        return events.stream().map(event -> {
            GameState gameState = gameStateRepository.findByEventId(event.getId()).orElse(null);
            return toResponse(event, gameState);
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
        event.setConfiguredPlayerCount(request.configuredPlayerCount());
        event.setTimeLimitMinutes(resolveTimeLimit(request.timeLimitMinutes()));
        event.setVillainDefinition(selectedVillain);
        applyVillainSnapshot(event, selectedVillain);
        event = eventRepository.save(event);

        GameState gameState = findGameState(eventId);
        gameState.setVillainHpMax(villainFormulaService.computeVillainHp(
            event.getVillainHpRuleTypeSnapshot(),
            event.getVillainHpRuleValueSnapshot(),
            request.configuredPlayerCount()
        ));
        gameState.setVillainHpCurrent(Math.min(gameState.getVillainHpCurrent(), gameState.getVillainHpMax()));
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

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_UPDATED,
            null, event.getName(), "Evento actualizado antes del inicio.");

        return toResponse(event, gameState);
    }

    @Transactional
    public void deleteEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerForEvent(event);
        ensureEditable(event);
        interPodCardService.deleteCardsForEvent(eventId);
        gameStateRepository.findByEventId(eventId).ifPresent(gameStateRepository::delete);
        eventLogRepository.deleteAll(eventLogRepository.findByEventIdOrderByCreatedAtDesc(eventId));
        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);
        GameState gameState = findGameState(eventId);
        return toResponse(event, gameState);
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

    private EventResponse toResponse(Event event, GameState gameState) {
        Long villainDefinitionId = event.getVillainDefinition() != null
                ? event.getVillainDefinition().getId()
                : null;

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
                event.getStartedAt(),
                event.getFinishedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}