package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.setup.AssignGroupPodRequest;
import com.tfg.gonzalo.backend.dto.setup.CreateGroupRequest;
import com.tfg.gonzalo.backend.dto.setup.CreatePodRequest;
import com.tfg.gonzalo.backend.dto.setup.EventStructureResponse;
import com.tfg.gonzalo.backend.dto.setup.GroupResponse;
import com.tfg.gonzalo.backend.dto.setup.PodResponse;
import com.tfg.gonzalo.backend.service.EventSetupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}")
public class EventSetupController {

    private final EventSetupService eventSetupService;

    public EventSetupController(EventSetupService eventSetupService) {
        this.eventSetupService = eventSetupService;
    }

    @PostMapping("/pods")
    @ResponseStatus(HttpStatus.CREATED)
    public PodResponse createPod(@PathVariable Long eventId,
                                 @Valid @RequestBody CreatePodRequest request) {
        return eventSetupService.createPod(eventId, request);
    }

    @PostMapping("/pods/{podId}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@PathVariable Long eventId,
                                     @PathVariable Long podId,
                                     @Valid @RequestBody CreateGroupRequest request) {
        return eventSetupService.createGroup(eventId, podId, request);
    }

    @PatchMapping("/groups/{groupId}/pod")
    public GroupResponse assignGroupToPod(@PathVariable Long eventId,
                                          @PathVariable Long groupId,
                                          @Valid @RequestBody AssignGroupPodRequest request) {
        return eventSetupService.assignGroupToPod(eventId, groupId, request);
    }

    @GetMapping("/structure")
    public EventStructureResponse getEventStructure(@PathVariable Long eventId) {
        return eventSetupService.getEventStructure(eventId);
    }
}