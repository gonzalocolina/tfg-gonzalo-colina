package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.AssignCoOrganizerRequest;
import com.tfg.gonzalo.backend.dto.event.CoOrganizerResponse;
import com.tfg.gonzalo.backend.service.EventRoleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/roles")
public class EventRoleController {

    private final EventRoleService eventRoleService;

    public EventRoleController(EventRoleService eventRoleService) {
        this.eventRoleService = eventRoleService;
    }

    @PostMapping("/co-organizers")
    @ResponseStatus(HttpStatus.CREATED)
    public CoOrganizerResponse assignCoOrganizer(@PathVariable Long eventId,
                                                 @Valid @RequestBody AssignCoOrganizerRequest request) {
        return eventRoleService.assignCoOrganizer(eventId, request);
    }

    @GetMapping("/co-organizers")
    public List<CoOrganizerResponse> listCoOrganizers(@PathVariable Long eventId) {
        return eventRoleService.listCoOrganizers(eventId);
    }
}
