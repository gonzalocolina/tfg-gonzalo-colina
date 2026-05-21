package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.EventLogEntryResponse;
import com.tfg.gonzalo.backend.dto.event.RevertEventLogRequest;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateOperationResponse;
import com.tfg.gonzalo.backend.model.enums.EventActionType;
import com.tfg.gonzalo.backend.service.EventLogService;
import com.tfg.gonzalo.backend.service.GameStateService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/logs")
public class EventLogController {

    private final EventLogService eventLogService;
    private final GameStateService gameStateService;

    public EventLogController(EventLogService eventLogService,
                              GameStateService gameStateService) {
        this.eventLogService = eventLogService;
        this.gameStateService = gameStateService;
    }

    @GetMapping
    public List<EventLogEntryResponse> getLogs(@PathVariable Long eventId,
                                               @RequestParam(required = false) Long groupId,
                                               @RequestParam(required = false) EventActionType actionType) {
        return eventLogService.getLogs(eventId, groupId, actionType);
    }

    @PostMapping("/{logId}/revert")
    public GameStateOperationResponse revertLog(@PathVariable Long eventId,
                                                @PathVariable Long logId,
                                                @Valid @RequestBody(required = false) RevertEventLogRequest request) {
        return gameStateService.revertLog(eventId, logId, request == null ? new RevertEventLogRequest(null) : request);
    }
}
