package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.RevertEventLogRequest;
import com.tfg.gonzalo.backend.dto.gamestate.DelegatedRemoveThreatRequest;
import com.tfg.gonzalo.backend.dto.gamestate.DelegatedThreatRequest;
import com.tfg.gonzalo.backend.dto.gamestate.DelegatedVillainDamageRequest;
import com.tfg.gonzalo.backend.dto.gamestate.FinalSummaryResponse;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateOperationResponse;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateSnapshotResponse;
import com.tfg.gonzalo.backend.dto.gamestate.ManualThreatUpdateRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ManualVillainHpUpdateRequest;
import com.tfg.gonzalo.backend.dto.gamestate.RemoveThreatRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ReportThreatRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ReportVillainDamageRequest;
import com.tfg.gonzalo.backend.dto.gamestate.TimerAdjustRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.EventLog;
import com.tfg.gonzalo.backend.model.Pod;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.GameFinishCause;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.PodRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GameStateService {

    private static final String LEGACY_UNASSIGNED_POD_NAME = "Sin asignar";
    private static final String CORRECTION_TARGET_VILLAIN_HP = "la vida del villano";
    private static final String CORRECTION_TARGET_THREAT = "la amenaza";

    private final EventRepository eventRepository;
    private final EventGroupRepository eventGroupRepository;
    private final PodRepository podRepository;
    private final EventLogRepository eventLogRepository;
    private final EventLogService eventLogService;
    private final AccessControlService accessControlService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public GameStateService(EventRepository eventRepository,
                            EventGroupRepository eventGroupRepository,
                            PodRepository podRepository,
                            EventLogRepository eventLogRepository,
                            EventLogService eventLogService,
                            AccessControlService accessControlService,
                            RealtimeEventPublisher realtimeEventPublisher) {
        this.eventRepository = eventRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.podRepository = podRepository;
        this.eventLogRepository = eventLogRepository;
        this.eventLogService = eventLogService;
        this.accessControlService = accessControlService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Transactional
    public GameStateSnapshotResponse getSnapshot(Long eventId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);
        List<String> notifications = new ArrayList<>();
        refreshTimerState(event, notifications);
        return persistAndPublishSnapshot(eventId, "GET_SNAPSHOT", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse reportVillainDamage(Long eventId, ReportVillainDamageRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        EventGroup group = findRepresentativeGroup(eventId, request.representativeCode());
        accessControlService.requireRepresentativeForGroup(event, group, request.representativeCode());
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureNotFinished(event);
        ensureRepresentativeActionsAllowed(event);

        int before = event.getVillainHpCurrent();
        int newHp = Math.max(0, before - request.damage());
        event.setVillainHpCurrent(newHp);
        event.setTotalReportedVillainDamage(event.getTotalReportedVillainDamage() + request.damage());

        updateVillainHpMilestones(event, before, newHp, notifications);
        if (newHp == 0) {
            finishGame(event, EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO, notifications);
        }

        eventLogService.appendAuthenticatedLog(event, group, EventActionType.VILLAIN_DAMAGE_REPORTED,
                String.valueOf(before), String.valueOf(newHp), "Daño reportado al villano por la mesa.");
        return persistAndRespond(eventId, "REPORT_VILLAIN_DAMAGE", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse reportThreat(Long eventId, ReportThreatRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        EventGroup group = findRepresentativeGroup(eventId, request.representativeCode());
        accessControlService.requireRepresentativeForGroup(event, group, request.representativeCode());
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureNotFinished(event);
        ensureRepresentativeActionsAllowed(event);

        int before = event.getSchemeThreatCurrent();
        int newThreat = before + request.threat();
        event.setSchemeThreatCurrent(newThreat);
        updateThreatMilestones(event, before, newThreat, notifications);

        if (newThreat >= event.getSchemeThreatTarget() && event.getSchemeThreatTarget() > 0) {
            finishGame(event, EventResult.THREAT_DEFEAT, GameFinishCause.THREAT_TARGET_REACHED, notifications);
        }

        eventLogService.appendAuthenticatedLog(event, group, EventActionType.THREAT_REPORTED,
                String.valueOf(before), String.valueOf(newThreat), "Amenaza reportada por la mesa.");
        return persistAndRespond(eventId, "REPORT_THREAT", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse removeThreat(Long eventId, RemoveThreatRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        EventGroup group = findRepresentativeGroup(eventId, request.representativeCode());
        accessControlService.requireRepresentativeForGroup(event, group, request.representativeCode());
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureNotFinished(event);
        ensureRepresentativeActionsAllowed(event);

        int before = event.getSchemeThreatCurrent();
        int newThreat = Math.max(0, before - request.threat());
        event.setSchemeThreatCurrent(newThreat);
        updateThreatMilestones(event, before, newThreat, notifications);

        eventLogService.appendAuthenticatedLog(event, group, EventActionType.THREAT_REMOVED,
                String.valueOf(before), String.valueOf(newThreat), "Amenaza retirada del plan principal por la mesa.");
        return persistAndRespond(eventId, "REMOVE_THREAT", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse delegateVillainDamage(Long eventId, DelegatedVillainDamageRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.VILLAIN_HP);
        EventGroup group = findGroup(eventId, request.groupId());
        List<String> notifications = new ArrayList<>();

        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_VILLAIN_HP);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_VILLAIN_HP);
        ensureDelegatedMetricActionsAllowed(event);

        int before = event.getVillainHpCurrent();
        int newHp = Math.max(0, before - request.damage());
        event.setVillainHpCurrent(newHp);
        event.setTotalReportedVillainDamage(event.getTotalReportedVillainDamage() + request.damage());

        updateVillainHpMilestones(event, before, newHp, notifications);

        eventLogService.appendAuthenticatedLog(
                event,
                group,
                EventActionType.VILLAIN_DAMAGE_DELEGATED,
                String.valueOf(before),
                String.valueOf(newHp),
                buildOptionalJustificationNote("Registro delegado de daño al villano en nombre de la mesa.", request.justification())
        );
        reconcileMetricOutcome(event, notifications,
                "Partida reabierta tras una corrección delegada de daño al villano.");
        return persistAndRespond(eventId, "DELEGATE_VILLAIN_DAMAGE", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse delegateRemoveVillainDamage(Long eventId, DelegatedVillainDamageRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.VILLAIN_HP);
        EventGroup group = findGroup(eventId, request.groupId());
        List<String> notifications = new ArrayList<>();

        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_VILLAIN_HP);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_VILLAIN_HP);
        ensureDelegatedMetricActionsAllowed(event);

        int before = event.getVillainHpCurrent();
        int newHp = Math.min(event.getVillainHpMax(), before + request.damage());
        int restoredHp = newHp - before;
        event.setVillainHpCurrent(newHp);
        event.setTotalReportedVillainDamage(Math.max(0,
                event.getTotalReportedVillainDamage() - restoredHp));

        updateVillainHpMilestones(event, before, newHp, notifications);

        eventLogService.appendAuthenticatedLog(
                event,
                group,
                EventActionType.VILLAIN_DAMAGE_REVERTED,
                String.valueOf(before),
                String.valueOf(newHp),
                buildOptionalJustificationNote(
                        "Reversión delegada de daño al villano en nombre de la mesa.",
                        request.justification())
        );
        reconcileMetricOutcome(event, notifications,
                "Partida reabierta tras revertir daño delegado de una mesa.");
        return persistAndRespond(eventId, "DELEGATE_REMOVE_VILLAIN_DAMAGE", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse delegateThreat(Long eventId, DelegatedThreatRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.THREAT);
        EventGroup group = findGroup(eventId, request.groupId());
        List<String> notifications = new ArrayList<>();

        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_THREAT);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_THREAT);
        ensureDelegatedMetricActionsAllowed(event);

        int before = event.getSchemeThreatCurrent();
        int newThreat = before + request.threat();
        event.setSchemeThreatCurrent(newThreat);
        updateThreatMilestones(event, before, newThreat, notifications);

        eventLogService.appendAuthenticatedLog(
                event,
                group,
                EventActionType.THREAT_DELEGATED,
                String.valueOf(before),
                String.valueOf(newThreat),
                buildOptionalJustificationNote("Registro delegado de amenaza en nombre de la mesa.", request.justification())
        );
        reconcileMetricOutcome(event, notifications,
                "Partida reabierta tras una corrección delegada de amenaza.");
        return persistAndRespond(eventId, "DELEGATE_THREAT", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse delegateRemoveThreat(Long eventId, DelegatedRemoveThreatRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.THREAT);
        EventGroup group = findGroup(eventId, request.groupId());
        List<String> notifications = new ArrayList<>();

        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_THREAT);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);
        ensureMetricCorrectionAllowed(event, CORRECTION_TARGET_THREAT);
        ensureDelegatedMetricActionsAllowed(event);

        int before = event.getSchemeThreatCurrent();
        int newThreat = Math.max(0, before - request.threat());
        event.setSchemeThreatCurrent(newThreat);
        updateThreatMilestones(event, before, newThreat, notifications);

        eventLogService.appendAuthenticatedLog(
                event,
                group,
                EventActionType.THREAT_REMOVED_DELEGATED,
                String.valueOf(before),
                String.valueOf(newThreat),
                buildOptionalJustificationNote(
                        "Registro delegado de retirada de amenaza del plan principal en nombre de la mesa.",
                        request.justification())
        );
        reconcileMetricOutcome(event, notifications,
                "Partida reabierta tras una retirada delegada de amenaza.");
        return persistAndRespond(eventId, "DELEGATE_REMOVE_THREAT", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse updateVillainHp(Long eventId, ManualVillainHpUpdateRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.VILLAIN_HP);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        refreshTimerState(event, notifications);
        ensureNotFinished(event);

        int before = event.getVillainHpCurrent();
        int newHp = Math.min(event.getVillainHpMax(), request.value());
        event.setVillainHpCurrent(newHp);

        updateVillainHpMilestones(event, before, newHp, notifications);
        if (newHp == 0) {
            finishGame(event, EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO, notifications);
        }

        eventLogService.appendAuthenticatedLog(
                event,
                EventActionType.VILLAIN_HP_CORRECTED,
                String.valueOf(before),
                String.valueOf(newHp),
                buildOptionalJustificationNote("Corrección manual global de HP del villano.", request.justification())
        );
        return persistAndRespond(eventId, "MANUAL_VILLAIN_HP_UPDATE", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse updateThreat(Long eventId, ManualThreatUpdateRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.THREAT);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        refreshTimerState(event, notifications);
        ensureNotFinished(event);

        int before = event.getSchemeThreatCurrent();
        int newThreat = request.value();
        event.setSchemeThreatCurrent(newThreat);

        updateThreatMilestones(event, before, newThreat, notifications);
        if (newThreat >= event.getSchemeThreatTarget() && event.getSchemeThreatTarget() > 0) {
            finishGame(event, EventResult.THREAT_DEFEAT, GameFinishCause.THREAT_TARGET_REACHED, notifications);
        }

        eventLogService.appendAuthenticatedLog(
                event,
                EventActionType.THREAT_CORRECTED,
                String.valueOf(before),
                String.valueOf(newThreat),
                buildOptionalJustificationNote("Corrección manual global de amenaza.", request.justification())
        );
        return persistAndRespond(eventId, "MANUAL_THREAT_UPDATE", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse revertLog(Long eventId, Long logId, RevertEventLogRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        EventLog originalLog = eventLogRepository.findByIdAndEventId(logId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el log solicitado para este evento."));
        List<String> notifications = new ArrayList<>();

        requireResponsibilityForRevertedLog(event, originalLog);
        ensureMetricCorrectionAllowed(event, describeCorrectionTarget(originalLog.getActionType()));
        refreshTimerState(event, notifications);
        ensureMetricCorrectionAllowed(event, describeCorrectionTarget(originalLog.getActionType()));
        ensureLogCanBeReverted(eventId, originalLog);

        ReversionResolution resolution = buildReversionResolution(originalLog, event);
        if (resolution.metric() == ReversionMetric.VILLAIN_HP) {
            int before = event.getVillainHpCurrent();
            event.setVillainHpCurrent(resolution.newValue());
            event.setTotalReportedVillainDamage(Math.max(0,
                    event.getTotalReportedVillainDamage() + resolution.totalReportedVillainDamageDelta()));
            updateVillainHpMilestones(event, before, resolution.newValue(), notifications);
        } else {
            int before = event.getSchemeThreatCurrent();
            event.setSchemeThreatCurrent(resolution.newValue());
            updateThreatMilestones(event, before, resolution.newValue(), notifications);
        }

        eventLogService.appendAuthenticatedLog(
                event,
                resolution.group(),
                resolution.reversalActionType(),
                String.valueOf(resolution.previousValue()),
                String.valueOf(resolution.newValue()),
                buildLogReversalNote(originalLog, request.justification()),
                originalLog
        );

        reconcileMetricOutcome(event, notifications,
                "Partida reabierta tras revertir un registro del log.");
        return persistAndRespond(eventId, "REVERT_LOG", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse startGame(Long eventId) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireOrganizerOrCoOrganizerForEvent(event);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        ensureStructureReadyForGame(eventId);
        markEventAsStarted(event);
        refreshTimerState(event, notifications);

        eventLogService.appendAuthenticatedLog(event, EventActionType.EVENT_STARTED,
                null, EventStatus.IN_PROGRESS.name(), "Partida iniciada manualmente.");
        return persistAndRespond(eventId, "START_GAME", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse startTimer(Long eventId) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        ensureStructureReadyForGame(eventId);
        if (event.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            throw new BusinessRuleException("El evento no tiene temporizador configurado.");
        }
        markEventAsStarted(event);
        refreshTimerState(event, notifications);

        if (event.getTimerRemainingSeconds() <= 0) {
            throw new BusinessRuleException("El temporizador no puede iniciarse porque no queda tiempo.");
        }

        event.setTimerStatus(TimerStatus.RUNNING);
        event.setTimerStartedAt(Instant.now());
        event.setTimerPausedAt(null);

        eventLogService.appendAuthenticatedLog(event, EventActionType.TIMER_STARTED,
                null, String.valueOf(event.getTimerRemainingSeconds()), "Temporizador iniciado.");
        return persistAndRespond(eventId, "TIMER_START", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse pauseTimer(Long eventId) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        if (event.getTimerStatus() != TimerStatus.RUNNING) {
            throw new BusinessRuleException("Solo se puede pausar un temporizador en ejecución.");
        }

        refreshTimerState(event, notifications);
        if (event.getStatus() == EventStatus.FINISHED) {
            return persistAndRespond(eventId, "TIMER_PAUSE", event, notifications);
        }

        event.setTimerStatus(TimerStatus.PAUSED);
        event.setTimerPausedAt(Instant.now());
        event.setTimerStartedAt(null);

        eventLogService.appendAuthenticatedLog(event, EventActionType.TIMER_PAUSED,
                null, String.valueOf(event.getTimerRemainingSeconds()), "Temporizador pausado.");
        return persistAndRespond(eventId, "TIMER_PAUSE", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse resumeTimer(Long eventId) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        ensureStructureReadyForGame(eventId);
        if (event.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            throw new BusinessRuleException("El evento no tiene temporizador configurado.");
        }
        if (event.getTimerStatus() != TimerStatus.PAUSED && event.getTimerStatus() != TimerStatus.READY) {
            throw new BusinessRuleException("El temporizador solo puede reanudarse desde pausa o estado listo.");
        }

        if (event.getTimerRemainingSeconds() <= 0) {
            throw new BusinessRuleException("El temporizador no puede reanudarse porque no queda tiempo.");
        }

        markEventAsStarted(event);
        event.setTimerStatus(TimerStatus.RUNNING);
        event.setTimerStartedAt(Instant.now());
        event.setTimerPausedAt(null);

        eventLogService.appendAuthenticatedLog(event, EventActionType.TIMER_RESUMED,
                null, String.valueOf(event.getTimerRemainingSeconds()), "Temporizador reanudado.");
        return persistAndRespond(eventId, "TIMER_RESUME", event, notifications);
    }

    @Transactional
    public GameStateOperationResponse adjustTimer(Long eventId, TimerAdjustRequest request) {
        MutableEventContext context = findMutableEventContext(eventId);
        Event event = context.event();
        accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.TIMER);
        List<String> notifications = new ArrayList<>();

        ensureNotFinished(event);
        if (event.getTimerStatus() == TimerStatus.NOT_CONFIGURED) {
            throw new BusinessRuleException("El evento no tiene temporizador configurado.");
        }
        refreshTimerState(event, notifications);
        ensureNotFinished(event);

        int before = event.getTimerRemainingSeconds();
        int deltaSeconds = request.minutesDelta() * 60;
        int after = Math.max(0, before + deltaSeconds);
        event.setTimerRemainingSeconds(after);
        event.setTimerTotalSeconds(Math.max(after, event.getTimerTotalSeconds() + deltaSeconds));

        if (event.getTimerStatus() == TimerStatus.RUNNING) {
            event.setTimerStartedAt(Instant.now());
        }

        evaluateTimerMilestones(event, notifications);
        if (after == 0 && event.getStatus() != EventStatus.FINISHED) {
            finishGame(event, EventResult.TIMEOUT_DEFEAT, GameFinishCause.TIMER_EXPIRED, notifications);
        }

        eventLogService.appendAuthenticatedLog(event, EventActionType.TIMER_ADJUSTED,
                String.valueOf(before), String.valueOf(after), "Ajuste manual del temporizador.");
        return persistAndRespond(eventId, "TIMER_ADJUST", event, notifications);
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private MutableEventContext findMutableEventContext(Long eventId) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe estado global para el evento solicitado."));
        return new MutableEventContext(event);
    }

    private EventGroup findRepresentativeGroup(Long eventId, String representativeCode) {
        return eventGroupRepository.findByEventIdAndRepresentativeCode(eventId, representativeCode)
                .orElseThrow(() -> new BusinessRuleException("Código de representante inválido para el evento."));
    }

    private EventGroup findGroup(Long eventId, Long groupId) {
        return eventGroupRepository.findByIdAndEventId(groupId, eventId)
                .orElseThrow(() -> new BusinessRuleException("La mesa seleccionada no existe en el evento."));
    }

    private void ensureNotFinished(Event event) {
        if (event.getStatus() == EventStatus.FINISHED) {
            throw new BusinessRuleException("La partida ya ha terminado y no admite más cambios.");
        }
    }

    private void ensureMetricCorrectionAllowed(Event event, String targetDescription) {
        if (event.getStatus() != EventStatus.FINISHED) {
            return;
        }
        if (event.getFinishCause() == GameFinishCause.TIMER_EXPIRED) {
            throw new BusinessRuleException(
                    "No se puede corregir " + targetDescription + " porque la partida terminó por tiempo.");
        }
    }

    private void ensureStructureReadyForGame(Long eventId) {
        List<EventGroup> groups = eventGroupRepository.findByEventId(eventId);

        if (groups.isEmpty()) {
            throw new BusinessRuleException("No se puede iniciar ni continuar la partida sin al menos una mesa.");
        }

        boolean hasPendingAssignments = groups.stream()
                .anyMatch(group -> group.getPod() == null
                        || LEGACY_UNASSIGNED_POD_NAME.equalsIgnoreCase(group.getPod().getName()));
        if (hasPendingAssignments) {
            throw new BusinessRuleException("No se puede iniciar ni continuar la partida mientras haya mesas pendientes de asignación a un pod.");
        }

        List<Long> assignedPodIds = groups.stream()
                .map(EventGroup::getPod)
                .filter(this::isRealPod)
                .map(Pod::getId)
                .distinct()
                .toList();

        boolean hasEmptyPods = podRepository.findByEventIdOrderBySortOrderAsc(eventId).stream()
                .filter(this::isRealPod)
                .anyMatch(pod -> !assignedPodIds.contains(pod.getId()));
        if (hasEmptyPods) {
            throw new BusinessRuleException("No se puede iniciar ni continuar la partida mientras haya nodos sin mesas asignadas.");
        }
    }

    private boolean isRealPod(Pod pod) {
        return pod != null && !LEGACY_UNASSIGNED_POD_NAME.equalsIgnoreCase(pod.getName());
    }

    private void ensureRepresentativeActionsAllowed(Event event) {
        if (event.getTimerStatus() == TimerStatus.PAUSED) {
            throw new BusinessRuleException("La partida está pausada. Espera a que el organizador la reanude.");
        }
        if (event.getTimerStatus() == TimerStatus.READY) {
            throw new BusinessRuleException("No puedes realizar acciones hasta que el organizador inicie el contador.");
        }
    }

    private void ensureDelegatedMetricActionsAllowed(Event event) {
        if (event.getTimerStatus() == TimerStatus.READY) {
            throw new BusinessRuleException("No puedes realizar acciones hasta que el organizador inicie el contador.");
        }
    }

    private String buildOptionalJustificationNote(String prefix, String justification) {
        if (justification == null || justification.isBlank()) {
            return prefix;
        }
        return prefix + " Justificación: " + justification.trim();
    }

    private String buildLogReversalNote(EventLog originalLog, String justification) {
        return buildOptionalJustificationNote(
                "Reversión del log #" + originalLog.getId() + " (" + describeActionType(originalLog.getActionType()) + ").",
                justification);
    }

    private void markEventAsStarted(Event event) {
        if (event.getStatus() == EventStatus.DRAFT || event.getStatus() == EventStatus.READY) {
            event.setStatus(EventStatus.IN_PROGRESS);
            if (event.getStartedAt() == null) {
                event.setStartedAt(Instant.now());
            }
        }
    }

    private void refreshTimerState(Event event, List<String> notifications) {
        if (event.getTimerStatus() != TimerStatus.RUNNING || event.getTimerStartedAt() == null) {
            return;
        }

        Instant now = Instant.now();
        long elapsed = Duration.between(event.getTimerStartedAt(), now).getSeconds();
        if (elapsed <= 0) {
            return;
        }

        int before = event.getTimerRemainingSeconds();
        int after = (int) Math.max(0, before - elapsed);
        event.setTimerRemainingSeconds(after);
        event.setTimerStartedAt(now);

        evaluateTimerMilestones(event, notifications);
        if (after == 0 && event.getStatus() != EventStatus.FINISHED) {
            finishGame(event, EventResult.TIMEOUT_DEFEAT, GameFinishCause.TIMER_EXPIRED, notifications);
        }
    }

    private void updateVillainHpMilestones(Event event,
                                           int beforeHp,
                                           int newHp,
                                           List<String> notifications) {
        int half = (int) Math.ceil(event.getVillainHpMax() * 0.5);
        int quarter = (int) Math.ceil(event.getVillainHpMax() * 0.25);

        if (beforeHp > half && newHp <= half) {
            notifications.add("VILLAIN_HP_50_PERCENT");
        }
        if (beforeHp > quarter && newHp <= quarter) {
            notifications.add("VILLAIN_HP_25_PERCENT");
        }

        event.setVillainHalfHpNotified(newHp <= half);
        event.setVillainQuarterHpNotified(newHp <= quarter);
    }

    private void updateThreatMilestones(Event event,
                                        int beforeThreat,
                                        int newThreat,
                                        List<String> notifications) {
        if (event.getSchemeThreatTarget() <= 0) {
            return;
        }
        int half = (int) Math.ceil(event.getSchemeThreatTarget() * 0.5);
        int threeQuarter = (int) Math.ceil(event.getSchemeThreatTarget() * 0.75);

        if (beforeThreat < half && newThreat >= half) {
            notifications.add("THREAT_50_PERCENT");
        }
        if (beforeThreat < threeQuarter && newThreat >= threeQuarter) {
            notifications.add("THREAT_75_PERCENT");
        }
        if (beforeThreat < event.getSchemeThreatTarget() && newThreat >= event.getSchemeThreatTarget()) {
            notifications.add("THREAT_100_PERCENT");
        }

        event.setThreatHalfNotified(newThreat >= half);
        event.setThreatThreeQuarterNotified(newThreat >= threeQuarter);
    }

    private void evaluateTimerMilestones(Event event, List<String> notifications) {
        int remaining = event.getTimerRemainingSeconds();
        if (!event.isTimerThirtyMinutesNotified() && remaining <= 1800) {
            event.setTimerThirtyMinutesNotified(true);
            notifications.add("TIMER_30_MINUTES");
        }
        if (!event.isTimerFifteenMinutesNotified() && remaining <= 900) {
            event.setTimerFifteenMinutesNotified(true);
            notifications.add("TIMER_15_MINUTES");
        }
        if (!event.isTimerFiveMinutesNotified() && remaining <= 300) {
            event.setTimerFiveMinutesNotified(true);
            notifications.add("TIMER_5_MINUTES");
        }
    }

    private void finishGame(Event event,
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
        event.setFinishCause(cause);
        event.setTimerStatus(TimerStatus.FINISHED);
        event.setTimerStartedAt(null);
        event.setTimerPausedAt(now);

        notifications.add("EVENT_FINISHED_" + result.name());
        eventLogService.appendSystemLog(event, EventActionType.EVENT_FINISHED,
                null, result.name(), "Partida finalizada por " + cause.name());
    }

    private void reconcileMetricOutcome(Event event,
                                        List<String> notifications,
                                        String reopenNote) {
        OutcomeResolution desiredOutcome = resolveOutcome(event);

        if (event.getStatus() == EventStatus.FINISHED) {
            if (event.getFinishCause() == GameFinishCause.TIMER_EXPIRED) {
                return;
            }

            if (desiredOutcome == null) {
                reopenGame(event, reopenNote);
                return;
            }

            event.setResult(desiredOutcome.result());
            event.setFinishCause(desiredOutcome.cause());
            return;
        }

        if (desiredOutcome != null) {
            finishGame(event, desiredOutcome.result(), desiredOutcome.cause(), notifications);
        }
    }

    private OutcomeResolution resolveOutcome(Event event) {
        if (event.getVillainHpCurrent() <= 0) {
            return new OutcomeResolution(EventResult.PLAYERS_VICTORY, GameFinishCause.VILLAIN_HP_ZERO);
        }
        if (event.getSchemeThreatTarget() > 0 && event.getSchemeThreatCurrent() >= event.getSchemeThreatTarget()) {
            return new OutcomeResolution(EventResult.THREAT_DEFEAT, GameFinishCause.THREAT_TARGET_REACHED);
        }
        return null;
    }

    private void reopenGame(Event event, String note) {
        Instant now = Instant.now();
        event.setStatus(EventStatus.IN_PROGRESS);
        event.setResult(null);
        event.setFinishedAt(null);
        event.setFinishCause(null);
        event.setTimerStartedAt(null);
        if (event.getTimerStatus() != TimerStatus.NOT_CONFIGURED) {
            event.setTimerStatus(TimerStatus.PAUSED);
            event.setTimerPausedAt(now);
        }

        eventLogService.appendAuthenticatedLog(event, EventActionType.EVENT_REOPENED,
                null, EventStatus.IN_PROGRESS.name(), note);
    }

    private void requireResponsibilityForRevertedLog(Event event, EventLog log) {
        switch (resolveReversionMetric(log.getActionType())) {
            case VILLAIN_HP -> accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.VILLAIN_HP);
            case THREAT -> accessControlService.requireGlobalResponsibility(event, CoOrganizerResponsibility.THREAT);
            case NONE -> throw new BusinessRuleException("Este tipo de log no puede revertirse desde el panel de partida.");
        }
    }

    private void ensureLogCanBeReverted(Long eventId, EventLog log) {
        if (resolveReversionMetric(log.getActionType()) == ReversionMetric.NONE) {
            throw new BusinessRuleException("Este tipo de log no admite reversión automática.");
        }
        if (eventLogRepository.existsByEventIdAndRevertedLogId(eventId, log.getId())) {
            throw new BusinessRuleException("Ese log ya ha sido revertido previamente.");
        }
        if (parseLogNumericValue(log.getPreviousValue()) == null || parseLogNumericValue(log.getNewValue()) == null) {
            throw new BusinessRuleException("El log seleccionado no contiene suficientes datos numéricos para revertirlo.");
        }
    }

    private ReversionResolution buildReversionResolution(EventLog originalLog, Event event) {
        int originalPreviousValue = requireNumericLogValue(originalLog.getPreviousValue(), "previousValue");
        int originalNewValue = requireNumericLogValue(originalLog.getNewValue(), "newValue");

        return switch (originalLog.getActionType()) {
            case VILLAIN_DAMAGE_REPORTED, VILLAIN_DAMAGE_DELEGATED -> {
                int deltaHp = originalPreviousValue - originalNewValue;
                int newHp = Math.min(event.getVillainHpMax(), event.getVillainHpCurrent() + deltaHp);
                yield new ReversionResolution(
                        ReversionMetric.VILLAIN_HP,
                        event.getVillainHpCurrent(),
                        newHp,
                        originalLog.getGroup(),
                        EventActionType.VILLAIN_DAMAGE_REVERTED,
                        originalNewValue - originalPreviousValue
                );
            }
            case VILLAIN_HP_CORRECTED -> {
                int deltaHp = originalPreviousValue - originalNewValue;
                int newHp = Math.max(0, Math.min(event.getVillainHpMax(), event.getVillainHpCurrent() + deltaHp));
                yield new ReversionResolution(
                        ReversionMetric.VILLAIN_HP,
                        event.getVillainHpCurrent(),
                        newHp,
                        originalLog.getGroup(),
                        EventActionType.VILLAIN_HP_CORRECTED,
                        originalNewValue - originalPreviousValue
                );
            }
            case THREAT_REPORTED, THREAT_DELEGATED -> {
                int deltaThreat = originalNewValue - originalPreviousValue;
                int newThreat = Math.max(0, event.getSchemeThreatCurrent() - deltaThreat);
                yield new ReversionResolution(
                        ReversionMetric.THREAT,
                        event.getSchemeThreatCurrent(),
                        newThreat,
                        originalLog.getGroup(),
                        EventActionType.THREAT_ADDITION_REVERTED,
                        0
                );
            }
            case THREAT_REMOVED, THREAT_REMOVED_DELEGATED -> {
                int deltaThreat = originalPreviousValue - originalNewValue;
                int newThreat = event.getSchemeThreatCurrent() + deltaThreat;
                yield new ReversionResolution(
                        ReversionMetric.THREAT,
                        event.getSchemeThreatCurrent(),
                        newThreat,
                        originalLog.getGroup(),
                        EventActionType.THREAT_REMOVAL_REVERTED,
                        0
                );
            }
            case THREAT_CORRECTED -> {
                int deltaThreat = originalPreviousValue - originalNewValue;
                int newThreat = Math.max(0, event.getSchemeThreatCurrent() + deltaThreat);
                yield new ReversionResolution(
                        ReversionMetric.THREAT,
                        event.getSchemeThreatCurrent(),
                        newThreat,
                        originalLog.getGroup(),
                        EventActionType.THREAT_CORRECTED,
                        0
                );
            }
            default -> throw new BusinessRuleException("Este tipo de log no admite reversión automática.");
        };
    }

    private Integer parseLogNumericValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int requireNumericLogValue(String value, String fieldName) {
        Integer parsedValue = parseLogNumericValue(value);
        if (parsedValue == null) {
            throw new BusinessRuleException("El log seleccionado no contiene un valor numérico válido en " + fieldName + ".");
        }
        return parsedValue;
    }

    private ReversionMetric resolveReversionMetric(EventActionType actionType) {
        return switch (actionType) {
            case VILLAIN_DAMAGE_REPORTED, VILLAIN_DAMAGE_DELEGATED, VILLAIN_HP_CORRECTED -> ReversionMetric.VILLAIN_HP;
            case THREAT_REPORTED, THREAT_DELEGATED, THREAT_REMOVED, THREAT_REMOVED_DELEGATED, THREAT_CORRECTED -> ReversionMetric.THREAT;
            default -> ReversionMetric.NONE;
        };
    }

    private String describeCorrectionTarget(EventActionType actionType) {
        return switch (resolveReversionMetric(actionType)) {
            case VILLAIN_HP -> CORRECTION_TARGET_VILLAIN_HP;
            case THREAT -> CORRECTION_TARGET_THREAT;
            case NONE -> "este elemento";
        };
    }

    private String describeActionType(EventActionType actionType) {
        return switch (actionType) {
            case VILLAIN_DAMAGE_REPORTED -> "daño al villano reportado";
            case VILLAIN_DAMAGE_DELEGATED -> "daño al villano delegado";
            case THREAT_REPORTED -> "amenaza reportada";
            case THREAT_DELEGATED -> "amenaza delegada";
            case THREAT_REMOVED -> "retirada de amenaza";
            case THREAT_REMOVED_DELEGATED -> "retirada de amenaza delegada";
            case VILLAIN_HP_CORRECTED -> "corrección manual de HP del villano";
            case THREAT_CORRECTED -> "corrección manual de amenaza";
            default -> actionType.name();
        };
    }

    private GameStateSnapshotResponse toSnapshot(Event event) {
        FinalSummaryResponse finalSummary = null;
        if (event.getStatus() == EventStatus.FINISHED && event.getResult() != null) {
            long duration = 0;
            if (event.getStartedAt() != null && event.getFinishedAt() != null) {
                duration = Duration.between(event.getStartedAt(), event.getFinishedAt()).getSeconds();
            }
            finalSummary = new FinalSummaryResponse(
                    event.getResult(),
                    event.getFinishCause(),
                    duration,
                    event.getTotalReportedVillainDamage(),
                    event.getSchemeThreatCurrent()
            );
        }

        return new GameStateSnapshotResponse(
                event.getId(),
                event.getStatus(),
                event.getResult(),
                event.getVillain() == null ? null : event.getVillain().getName(),
                event.getVillain() == null ? null : event.getVillain().getType(),
                event.getVillainHpCurrent(),
                event.getVillainHpMax(),
                event.getSchemeThreatCurrent(),
                event.getSchemeThreatTarget(),
                event.getTimerRemainingSeconds(),
                event.getTimerStatus(),
                event.getTotalReportedVillainDamage(),
                event.getStartedAt(),
                event.getFinishedAt(),
                event.getUpdatedAt(),
                finalSummary
        );
    }

    private GameStateSnapshotResponse persistAndPublishSnapshot(Long eventId,
                                                                String operation,
                                                                Event event,
                                                                List<String> notifications) {
        eventRepository.save(event);
        GameStateSnapshotResponse snapshot = toSnapshot(event);
        publishRealtimeUpdate(eventId, operation, snapshot, notifications);
        return snapshot;
    }

    private GameStateOperationResponse persistAndRespond(Long eventId,
                                                         String operation,
                                                         Event event,
                                                         List<String> notifications) {
        GameStateSnapshotResponse snapshot = persistAndPublishSnapshot(eventId, operation, event, notifications);
        return new GameStateOperationResponse(snapshot, notifications);
    }

    private void publishRealtimeUpdate(Long eventId,
                                       String operation,
                                       GameStateSnapshotResponse snapshot,
                                       List<String> notifications) {
        realtimeEventPublisher.publishGameStateUpdate(eventId, operation, snapshot, notifications);
    }

    private record MutableEventContext(Event event) {
    }

    private record OutcomeResolution(EventResult result, GameFinishCause cause) {
    }

    private record ReversionResolution(ReversionMetric metric,
                                       int previousValue,
                                       int newValue,
                                       EventGroup group,
                                       EventActionType reversalActionType,
                                       int totalReportedVillainDamageDelta) {
    }

    private enum ReversionMetric {
        VILLAIN_HP,
        THREAT,
        NONE
    }
}