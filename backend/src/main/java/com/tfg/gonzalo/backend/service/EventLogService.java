package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.dto.event.EventLogEntryResponse;
import com.tfg.gonzalo.backend.exception.ForbiddenOperationException;
import com.tfg.gonzalo.backend.exception.ResourceNotFoundException;
import com.tfg.gonzalo.backend.model.AppUser;
import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventGroup;
import com.tfg.gonzalo.backend.model.EventLog;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.repository.AppUserRepository;
import com.tfg.gonzalo.backend.repository.EventLogRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.security.AuthPrincipal;
import com.tfg.gonzalo.backend.security.AccessControlService;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventLogService {

    private final EventRepository eventRepository;
    private final EventLogRepository eventLogRepository;
    private final AppUserRepository appUserRepository;
    private final AccessControlService accessControlService;

    public EventLogService(EventRepository eventRepository,
                           EventLogRepository eventLogRepository,
                           AppUserRepository appUserRepository,
                           AccessControlService accessControlService) {
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.appUserRepository = appUserRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public List<EventLogEntryResponse> getLogs(Long eventId, Long groupId, EventActionType actionType) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el evento solicitado."));
        accessControlService.requireReadAccessToEvent(event);

        List<EventLog> logs;
        if (groupId != null && actionType != null) {
            logs = eventLogRepository.findByEventIdAndGroupIdAndActionTypeOrderByCreatedAtDesc(eventId, groupId, actionType);
        } else if (groupId != null) {
            logs = eventLogRepository.findByEventIdAndGroupIdOrderByCreatedAtDesc(eventId, groupId);
        } else if (actionType != null) {
            logs = eventLogRepository.findByEventIdAndActionTypeOrderByCreatedAtDesc(eventId, actionType);
        } else {
            logs = eventLogRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        }

        return logs.stream().map(this::mapLog).toList();
    }

    private EventLogEntryResponse mapLog(EventLog eventLog) {
        return new EventLogEntryResponse(
                eventLog.getId(),
                eventLog.getCreatedAt(),
            eventLog.getActor() == null ? null : eventLog.getActor().getUsername(),
                eventLog.getGroup() == null ? null : eventLog.getGroup().getIdentifier(),
                eventLog.getActionType(),
                eventLog.getPreviousValue(),
                eventLog.getNewValue(),
                eventLog.getNotes()
        );
    }

    public void appendLog(Event event,
                          AppUser actor,
                          EventGroup group,
                          EventActionType actionType,
                          String previousValue,
                          String newValue,
                          String notes) {
        EventLog eventLog = new EventLog();
        eventLog.setEvent(event);
        eventLog.setActor(actor);
        eventLog.setGroup(group);
        eventLog.setActionType(actionType);
        eventLog.setPreviousValue(previousValue);
        eventLog.setNewValue(newValue);
        eventLog.setNotes(notes);
        eventLogRepository.save(eventLog);
    }

    public void appendAuthenticatedLog(Event event,
                                       EventGroup group,
                                       EventActionType actionType,
                                       String previousValue,
                                       String newValue,
                                       String notes) {
        appendLog(event, resolveAuthenticatedActor(), group, actionType, previousValue, newValue, notes);
    }

    public void appendAuthenticatedLog(Event event,
                                       EventActionType actionType,
                                       String previousValue,
                                       String newValue,
                                       String notes) {
        appendAuthenticatedLog(event, null, actionType, previousValue, newValue, notes);
    }

    public void appendSystemLog(Event event,
                                EventGroup group,
                                EventActionType actionType,
                                String previousValue,
                                String newValue,
                                String notes) {
        appendLog(event, null, group, actionType, previousValue, newValue, notes);
    }

    public void appendSystemLog(Event event,
                                EventActionType actionType,
                                String previousValue,
                                String newValue,
                                String notes) {
        appendSystemLog(event, null, actionType, previousValue, newValue, notes);
    }

    public void appendOrganizerLog(Event event,
                                   EventGroup group,
                                   EventActionType actionType,
                                   String previousValue,
                                   String newValue,
                                   String notes) {
        appendLog(event, event.getOrganizer(), group, actionType, previousValue, newValue, notes);
    }

    public void appendOrganizerLog(Event event,
                                   EventActionType actionType,
                                   String previousValue,
                                   String newValue,
                                   String notes) {
        appendOrganizerLog(event, null, actionType, previousValue, newValue, notes);
    }

    private AppUser resolveAuthenticatedActor() {
        try {
            AuthPrincipal principal = accessControlService.requireAuthenticatedPrincipal();
            if (principal.userId() == null) {
                return null;
            }
            return appUserRepository.findById(principal.userId()).orElse(null);
        } catch (ForbiddenOperationException ex) {
            return null;
        }
    }
}
