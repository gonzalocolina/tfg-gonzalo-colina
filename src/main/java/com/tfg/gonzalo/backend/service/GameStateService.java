package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.gamestate.FinalSummaryResponse;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateOperationResponse;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateSnapshotResponse;
import com.tfg.gonzalo.backend.dto.gamestate.ManualThreatUpdateRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ManualVillainHpUpdateRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ReportThreatRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ReportVillainDamageRequest;
import com.tfg.gonzalo.backend.dto.gamestate.TimerAdjustRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GameFinishCause;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GameStateService {

    private final EventRepository eventRepository;
    private final GameStateRepository gameStateRepository;
    private final EventGroupRepository eventGroupRepository;
    private final EventLogService eventLogService;
    private final AccessControlService accessControlService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public GameStateService(EventRepository eventRepository,
                            GameStateRepository gameStateRepository,
                            EventGroupRepository eventGroupRepository,
                            EventLogService eventLogService,
                            AccessControlService accessControlService,
                            RealtimeEventPublisher realtimeEventPublisher) {
        this.eventRepository = eventRepository;
        this.gameStateRepository = gameStateRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.eventLogService = eventLogService;
        this.accessControlService = accessControlService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Transactional
    public GameStateSnapshotResponse getSnapshot(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();
        refreshTimerState(event, gameState, notifications);
        return persistAndPublishSnapshot(eventId, "GET_SNAPSHOT", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse reportVillainDamage(Long eventId, ReportVillainDamageRequest request) {
        Event event = findEvent(eventId);
        GameState gameState = findGameState(eventId);
        EventGroup group = findRepresentativeGroup(eventId, request.representativeCode());
        accessControlService.requireRepresentativeOrOrganizerForGroup(event, group, request.representativeCode());
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        markEventAsStarted(event);
        refreshTimerState(event, gameState, notifications);
        ensureNotFinished(event);

        int before = gameState.getVillainHpCurrent();
        int newHp = Math.max(0, before - request.damage());
        gameState.setVillainHpCurrent(newHp);
        gameState.setTotalReportedVillainDamage(gameState.getTotalReportedVillainDamage() + request.damage());

        evaluateVillainHpMilestones(gameState, notifications);
        if (newHp == 0) {
            finishGame(event, gameState, EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO, notifications);
        }

        eventLogService.appendOrganizerLog(event, group, EventActionType.VILLAIN_DAMAGE_REPORTED,
            String.valueOf(before), String.valueOf(newHp), "Daño reportado al villano.");
        return persistAndRespond(eventId, "REPORT_VILLAIN_DAMAGE", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse reportThreat(Long eventId, ReportThreatRequest request) {
        Event event = findEvent(eventId);
        GameState gameState = findGameState(eventId);
        EventGroup group = findRepresentativeGroup(eventId, request.representativeCode());
        accessControlService.requireRepresentativeOrOrganizerForGroup(event, group, request.representativeCode());
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        markEventAsStarted(event);
        refreshTimerState(event, gameState, notifications);
        ensureNotFinished(event);

        int before = gameState.getSchemeThreatCurrent();
        int newThreat = before + request.threat();
        gameState.setSchemeThreatCurrent(newThreat);

        evaluateThreatMilestones(gameState, notifications);
        if (newThreat >= gameState.getSchemeThreatTarget() && gameState.getSchemeThreatTarget() > 0) {
            finishGame(event, gameState, EventResult.THREAT_DEFEAT, GameFinishCause.THREAT_TARGET_REACHED, notifications);
        }

        eventLogService.appendOrganizerLog(event, group, EventActionType.THREAT_REPORTED,
            String.valueOf(before), String.valueOf(newThreat), "Amenaza reportada por la mesa.");
        return persistAndRespond(eventId, "REPORT_THREAT", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse updateVillainHp(Long eventId, ManualVillainHpUpdateRequest request) {
        Event event = findEvent(eventId);
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.VILLAIN_HP);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        refreshTimerState(event, gameState, notifications);

        int before = gameState.getVillainHpCurrent();
        int newHp = Math.min(gameState.getVillainHpMax(), request.value());
        gameState.setVillainHpCurrent(newHp);

        evaluateVillainHpMilestones(gameState, notifications);
        if (newHp == 0) {
            finishGame(event, gameState, EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO, notifications);
        }

        eventLogService.appendOrganizerLog(event, EventActionType.VILLAIN_HP_CORRECTED,
            String.valueOf(before), String.valueOf(newHp), "Corrección manual de HP del villano.");
        return persistAndRespond(eventId, "MANUAL_VILLAIN_HP_UPDATE", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse updateThreat(Long eventId, ManualThreatUpdateRequest request) {
        Event event = findEvent(eventId);
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.THREAT);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        refreshTimerState(event, gameState, notifications);

        int before = gameState.getSchemeThreatCurrent();
        int newThreat = request.value();
        gameState.setSchemeThreatCurrent(newThreat);

        evaluateThreatMilestones(gameState, notifications);
        if (newThreat >= gameState.getSchemeThreatTarget() && gameState.getSchemeThreatTarget() > 0) {
            finishGame(event, gameState, EventResult.THREAT_DEFEAT, GameFinishCause.THREAT_TARGET_REACHED, notifications);
        }

        eventLogService.appendOrganizerLog(event, EventActionType.THREAT_CORRECTED,
            String.valueOf(before), String.valueOf(newThreat), "Corrección manual de amenaza.");
        return persistAndRespond(eventId, "MANUAL_THREAT_UPDATE", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse startGame(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerOrCoOrganizerForEvent(event);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        markEventAsStarted(event);
        refreshTimerState(event, gameState, notifications);

        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_STARTED,
            null, EventStatus.IN_PROGRESS.name(), "Partida iniciada manualmente.");
        return persistAndRespond(eventId, "START_GAME", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse startTimer(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        if (gameState.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            throw new BusinessRuleException("El evento no tiene temporizador configurado.");
        }
        markEventAsStarted(event);
        refreshTimerState(event, gameState, notifications);

        if (gameState.getTimerRemainingSeconds() <= 0) {
            throw new BusinessRuleException("El temporizador no puede iniciarse porque no queda tiempo.");
        }

        gameState.setTimerStatus(TimerStatus.RUNNING);
        gameState.setTimerStartedAt(Instant.now());
        gameState.setTimerPausedAt(null);

        eventLogService.appendOrganizerLog(event, EventActionType.TIMER_STARTED,
            null, String.valueOf(gameState.getTimerRemainingSeconds()), "Temporizador iniciado.");
        return persistAndRespond(eventId, "TIMER_START", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse pauseTimer(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        if (gameState.getTimerStatus() != TimerStatus.RUNNING) {
            throw new BusinessRuleException("Solo se puede pausar un temporizador en ejecución.");
        }

        refreshTimerState(event, gameState, notifications);
        if (event.getStatus() == EventStatus.FINISHED) {
            return persistAndRespond(eventId, "TIMER_PAUSE", event, gameState, notifications);
        }

        gameState.setTimerStatus(TimerStatus.PAUSED);
        gameState.setTimerPausedAt(Instant.now());
        gameState.setTimerStartedAt(null);

        eventLogService.appendOrganizerLog(event, EventActionType.TIMER_PAUSED,
            null, String.valueOf(gameState.getTimerRemainingSeconds()), "Temporizador pausado.");
        return persistAndRespond(eventId, "TIMER_PAUSE", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse resumeTimer(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        if (gameState.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            throw new BusinessRuleException("El evento no tiene temporizador configurado.");
        }
        if (gameState.getTimerStatus() != TimerStatus.PAUSED && gameState.getTimerStatus() != TimerStatus.READY) {
            throw new BusinessRuleException("El temporizador solo puede reanudarse desde pausa o estado listo.");
        }

        if (gameState.getTimerRemainingSeconds() <= 0) {
            throw new BusinessRuleException("El temporizador no puede reanudarse porque no queda tiempo.");
        }

        markEventAsStarted(event);
        gameState.setTimerStatus(TimerStatus.RUNNING);
        gameState.setTimerStartedAt(Instant.now());
        gameState.setTimerPausedAt(null);

        eventLogService.appendOrganizerLog(event, EventActionType.TIMER_RESUMED,
            null, String.valueOf(gameState.getTimerRemainingSeconds()), "Temporizador reanudado.");
        return persistAndRespond(eventId, "TIMER_RESUME", event, gameState, notifications);
    }

    @Transactional
    public GameStateOperationResponse adjustTimer(Long eventId, TimerAdjustRequest request) {
        Event event = findEvent(eventId);
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        GameState gameState = findGameState(eventId);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        if (gameState.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            throw new BusinessRuleException("El evento no tiene temporizador configurado.");
        }
        refreshTimerState(event, gameState, notifications);
        ensureNotFinished(event);

        int before = gameState.getTimerRemainingSeconds();
        int deltaSeconds = request.minutesDelta() * 60;
        int after = Math.max(0, before + deltaSeconds);
        gameState.setTimerRemainingSeconds(after);
        gameState.setTimerTotalSeconds(Math.max(after, gameState.getTimerTotalSeconds() + deltaSeconds));

        if (gameState.getTimerStatus() == TimerStatus.RUNNING) {
            gameState.setTimerStartedAt(Instant.now());
        }

        evaluateTimerMilestones(gameState, notifications);
        if (after == 0 && event.getStatus() != EventStatus.FINISHED) {
            finishGame(event, gameState, EventResult.TIMEOUT_DEFEAT, GameFinishCause.TIMER_EXPIRED, notifications);
        }

        eventLogService.appendOrganizerLog(event, EventActionType.TIMER_ADJUSTED,
            String.valueOf(before), String.valueOf(after), "Ajuste manual del temporizador.");
        return persistAndRespond(eventId, "TIMER_ADJUST", event, gameState, notifications);
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private GameState findGameState(Long eventId) {
        return gameStateRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe estado global para el evento solicitado."));
    }

    private EventGroup findRepresentativeGroup(Long eventId, String representativeCode) {
        return eventGroupRepository.findByEventIdAndRepresentativeCode(eventId, representativeCode)
                .orElseThrow(() -> new BusinessRuleException("Código de representante inválido para el evento."));
    }

    private void ensureNotFinished(Event event) {
        if (event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("La partida ya ha terminado y no admite más cambios.");
        }
    }

    private void markEventAsStarted(Event event) {
        if (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.READY) {
            event.setStatus(EventStatus.IN_PROGRESS);
            if (event.getStartedAt() == null) {
                event.setStartedAt(Instant.now());
            }
        }
    }

    private void refreshTimerState(Event event, GameState gameState, List<String> notifications) {
        if (gameState.getTimerStatus() != TimerStatus.RUNNING || gameState.getTimerStartedAt() == null) {
            return;
        }

        Instant now = Instant.now();
        long elapsed = Duration.between(gameState.getTimerStartedAt(), now).getSeconds();
        if (elapsed <= 0) {
            return;
        }

        int before = gameState.getTimerRemainingSeconds();
        int after = (int) Math.max(0, before - elapsed);
        gameState.setTimerRemainingSeconds(after);
        gameState.setTimerStartedAt(now);

        evaluateTimerMilestones(gameState, notifications);
        if (after == 0 && event.getStatus() != EventStatus.FINISHED) {
            finishGame(event, gameState, EventResult.TIMEOUT_DEFEAT, GameFinishCause.TIMER_EXPIRED, notifications);
        }
    }

    private void evaluateVillainHpMilestones(GameState gameState, List<String> notifications) {
        int half = (int) Math.ceil(gameState.getVillainHpMax() * 0.5);
        int quarter = (int) Math.ceil(gameState.getVillainHpMax() * 0.25);

        if (!gameState.isVillainHalfHpNotified() && gameState.getVillainHpCurrent() <= half) {
            gameState.setVillainHalfHpNotified(true);
            notifications.add("VILLAIN_HP_50_PERCENT");
        }
        if (!gameState.isVillainQuarterHpNotified() && gameState.getVillainHpCurrent() <= quarter) {
            gameState.setVillainQuarterHpNotified(true);
            notifications.add("VILLAIN_HP_25_PERCENT");
        }
    }

    private void evaluateThreatMilestones(GameState gameState, List<String> notifications) {
        if (gameState.getSchemeThreatTarget() <= 0) {
            return;
        }
        int half = (int) Math.ceil(gameState.getSchemeThreatTarget() * 0.5);
        int threeQuarter = (int) Math.ceil(gameState.getSchemeThreatTarget() * 0.75);

        if (!gameState.isThreatHalfNotified() && gameState.getSchemeThreatCurrent() >= half) {
            gameState.setThreatHalfNotified(true);
            notifications.add("THREAT_50_PERCENT");
        }
        if (!gameState.isThreatThreeQuarterNotified() && gameState.getSchemeThreatCurrent() >= threeQuarter) {
            gameState.setThreatThreeQuarterNotified(true);
            notifications.add("THREAT_75_PERCENT");
        }
        if (gameState.getSchemeThreatCurrent() >= gameState.getSchemeThreatTarget()
                && !notifications.contains("THREAT_100_PERCENT")) {
            notifications.add("THREAT_100_PERCENT");
        }
    }

    private void evaluateTimerMilestones(GameState gameState, List<String> notifications) {
        int remaining = gameState.getTimerRemainingSeconds();
        if (!gameState.isTimerThirtyMinutesNotified() && remaining <= 1800) {
            gameState.setTimerThirtyMinutesNotified(true);
            notifications.add("TIMER_30_MINUTES");
        }
        if (!gameState.isTimerFifteenMinutesNotified() && remaining <= 900) {
            gameState.setTimerFifteenMinutesNotified(true);
            notifications.add("TIMER_15_MINUTES");
        }
        if (!gameState.isTimerFiveMinutesNotified() && remaining <= 300) {
            gameState.setTimerFiveMinutesNotified(true);
            notifications.add("TIMER_5_MINUTES");
        }
    }

    private void finishGame(Event event,
                            GameState gameState,
                            EventResult result,
                            GameFinishCause cause,
                            List<String> notifications) {
        if (event.getStatus() == EventStatus.FINISHED) {
            return;
        }

        Instant now = Instant.now();
        event.setStatus(EventStatus.FINISHED);
        event.setResult(result);
        if (event.getStartedAt() == null) {
            event.setStartedAt(now);
        }
        event.setFinishedAt(now);

        gameState.setResult(result);
        gameState.setFinishCause(cause);
        gameState.setFinishedAt(now);
        gameState.setTimerStatus(TimerStatus.FINISHED);
        gameState.setTimerStartedAt(null);
        gameState.setTimerPausedAt(now);

        notifications.add("EVENT_FINISHED_" + result.name());
        eventLogService.appendOrganizerLog(event, EventActionType.EVENT_FINISHED,
            null, result.name(), "Partida finalizada por " + cause.name());
    }

    private GameStateSnapshotResponse toSnapshot(Event event, GameState gameState) {
        FinalSummaryResponse finalSummary = null;
        if (event.getStatus() == EventStatus.FINISHED && gameState.getResult() != null) {
            long duration = 0;
            if (event.getStartedAt() != null && event.getFinishedAt() != null) {
                duration = Duration.between(event.getStartedAt(), event.getFinishedAt()).getSeconds();
            }
            finalSummary = new FinalSummaryResponse(
                    gameState.getResult(),
                    gameState.getFinishCause(),
                    duration,
                    gameState.getTotalReportedVillainDamage(),
                    gameState.getSchemeThreatCurrent()
            );
        }

        return new GameStateSnapshotResponse(
                event.getId(),
                event.getStatus(),
                event.getResult(),
            event.getVillainNameSnapshot(),
            event.getVillainTypeSnapshot(),
                gameState.getVillainHpCurrent(),
                gameState.getVillainHpMax(),
                gameState.getSchemeThreatCurrent(),
                gameState.getSchemeThreatTarget(),
                gameState.getTimerRemainingSeconds(),
                gameState.getTimerStatus(),
                gameState.getTotalReportedVillainDamage(),
                event.getStartedAt(),
                event.getFinishedAt(),
                gameState.getUpdatedAt(),
                finalSummary
        );
    }

    private GameStateSnapshotResponse persistAndPublishSnapshot(Long eventId,
                                                                String operation,
                                                                Event event,
                                                                GameState gameState,
                                                                List<String> notifications) {
        gameStateRepository.save(gameState);
        eventRepository.save(event);
        GameStateSnapshotResponse snapshot = toSnapshot(event, gameState);
        publishRealtimeUpdate(eventId, operation, snapshot, notifications);
        return snapshot;
    }

    private GameStateOperationResponse persistAndRespond(Long eventId,
                                                         String operation,
                                                         Event event,
                                                         GameState gameState,
                                                         List<String> notifications) {
        GameStateSnapshotResponse snapshot = persistAndPublishSnapshot(eventId, operation, event, gameState, notifications);
        return new GameStateOperationResponse(snapshot, notifications);
    }

    private void publishRealtimeUpdate(Long eventId,
                                       String operation,
                                       GameStateSnapshotResponse snapshot,
                                       List<String> notifications) {
        realtimeEventPublisher.publishGameStateUpdate(eventId, operation, snapshot, notifications);
    }
}