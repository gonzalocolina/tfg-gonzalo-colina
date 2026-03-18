package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.CreateInterPodAlertRequest;
import com.tfg.gonzalo.backend.dto.event.InterPodAlertResponse;
import com.tfg.gonzalo.backend.dto.event.UpdateInterPodAlertStatusRequest;
import com.tfg.gonzalo.backend.exception.BusinessRuleException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.EventLog;
import com.tfg.gonzalo.backend.model.InterPodAlert;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.InterPodAlertRepository;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InterPodAlertService {

    private final EventRepository eventRepository;
    private final EventGroupRepository eventGroupRepository;
    private final InterPodAlertRepository interPodAlertRepository;
    private final EventLogRepository eventLogRepository;
    private final AccessControlService accessControlService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public InterPodAlertService(EventRepository eventRepository,
                                EventGroupRepository eventGroupRepository,
                                InterPodAlertRepository interPodAlertRepository,
                                EventLogRepository eventLogRepository,
                                AccessControlService accessControlService,
                                RealtimeEventPublisher realtimeEventPublisher) {
        this.eventRepository = eventRepository;
        this.eventGroupRepository = eventGroupRepository;
        this.interPodAlertRepository = interPodAlertRepository;
        this.eventLogRepository = eventLogRepository;
        this.accessControlService = accessControlService;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Transactional
    public InterPodAlertResponse createAlert(Long eventId, CreateInterPodAlertRequest request) {
        Event event = findEvent(eventId);
        EventGroup group = eventGroupRepository.findByEventIdAndRepresentativeCode(eventId, request.representativeCode())
                .orElseThrow(() -> new BusinessRuleException("Código de representante inválido para el evento."));
        accessControlService.requireRepresentativeOrOrganizerForGroup(event, group, request.representativeCode());

        InterPodAlert alert = new InterPodAlert();
        alert.setEvent(event);
        alert.setPod(group.getPod());
        alert.setSourceGroup(group);
        alert.setCardType(request.cardType());
        alert.setStatus(normalizeStatus(request.status()));
        alert = interPodAlertRepository.save(alert);

        registerLog(event, group, EventActionType.INTER_POD_ALERT_CREATED, null,
                alert.getCardType().name(), "Notificación inter-pod registrada.");

        realtimeEventPublisher.publishGameStateUpdate(eventId, "INTER_POD_ALERT_CREATED", null,
                List.of("INTER_POD_ALERT_" + alert.getCardType().name()));

        return mapAlert(alert);
    }

    @Transactional
    public InterPodAlertResponse updateStatus(Long eventId, Long alertId, UpdateInterPodAlertStatusRequest request) {
        Event event = findEvent(eventId);
        accessControlService.requireOrganizerOrCoOrganizerForEvent(event);
        InterPodAlert alert = interPodAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la alerta inter-pod solicitada."));

        if (!alert.getEvent().getId().equals(eventId)) {
            throw new BusinessRuleException("La alerta no pertenece al evento indicado.");
        }

        String previousStatus = alert.getStatus();
        alert.setStatus(request.status().trim());
        alert = interPodAlertRepository.save(alert);

        registerLog(event, alert.getSourceGroup(), EventActionType.INTER_POD_ALERT_UPDATED,
                previousStatus, alert.getStatus(), "Estado de alerta inter-pod actualizado.");

        realtimeEventPublisher.publishGameStateUpdate(eventId, "INTER_POD_ALERT_UPDATED", null,
                List.of("INTER_POD_ALERT_STATUS_UPDATED"));

        return mapAlert(alert);
    }

    @Transactional
    public List<InterPodAlertResponse> listAlerts(Long eventId, Long podId) {
        Event event = findEvent(eventId);
        accessControlService.requireReadAccessToEvent(event);

        List<InterPodAlert> alerts = podId == null
                ? interPodAlertRepository.findByEventIdOrderByCreatedAtDesc(eventId)
            : interPodAlertRepository.findByEventIdAndPodIdOrderByCreatedAtDesc(eventId, podId);

        return alerts.stream().map(this::mapAlert).toList();
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim();
    }

    private InterPodAlertResponse mapAlert(InterPodAlert alert) {
        return new InterPodAlertResponse(
                alert.getId(),
                alert.getEvent().getId(),
                alert.getPod().getId(),
                alert.getPod().getName(),
                alert.getSourceGroup().getId(),
                alert.getSourceGroup().getIdentifier(),
                alert.getCardType(),
                alert.getStatus(),
                alert.getCreatedAt(),
                alert.getUpdatedAt()
        );
    }

    private void registerLog(Event event,
                             EventGroup group,
                             EventActionType actionType,
                             String previousValue,
                             String newValue,
                             String notes) {
        EventLog eventLog = new EventLog();
        eventLog.setEvent(event);
        eventLog.setActor(event.getOrganizer());
        eventLog.setGroup(group);
        eventLog.setActionType(actionType);
        eventLog.setPreviousValue(previousValue);
        eventLog.setNewValue(newValue);
        eventLog.setNotes(notes);
        eventLogRepository.save(eventLog);
    }
}
