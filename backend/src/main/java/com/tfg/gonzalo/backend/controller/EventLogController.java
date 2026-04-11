package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.EventLogEntryResponse;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.service.EventLogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/logs")
public class EventLogController {

    private final EventLogService eventLogService;

    public EventLogController(EventLogService eventLogService) {
        this.eventLogService = eventLogService;
    }

    @GetMapping
    public List<EventLogEntryResponse> getLogs(@PathVariable Long eventId,
                                               @RequestParam(required = false) Long groupId,
                                               @RequestParam(required = false) EventActionType actionType) {
        return eventLogService.getLogs(eventId, groupId, actionType);
    }
}
