package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.interpod.CreateInterPodCardDefinitionRequest;
import com.tfg.gonzalo.backend.dto.interpod.InterPodCardDefinitionResponse;
import com.tfg.gonzalo.backend.dto.interpod.UpdateInterPodCardDefinitionRequest;
import com.tfg.gonzalo.backend.service.InterPodCardDefinitionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InterPodCardDefinitionController {

    private final InterPodCardDefinitionService interPodCardDefinitionService;

    public InterPodCardDefinitionController(InterPodCardDefinitionService interPodCardDefinitionService) {
        this.interPodCardDefinitionService = interPodCardDefinitionService;
    }

    @GetMapping("/inter-pod-card-definitions")
    public List<InterPodCardDefinitionResponse> listDefinitions() {
        return interPodCardDefinitionService.listAvailableDefinitions();
    }

    @GetMapping("/events/{eventId}/inter-pod-card-definitions")
    public List<InterPodCardDefinitionResponse> listEventDefinitions(@PathVariable Long eventId) {
        return interPodCardDefinitionService.listSelectedDefinitionsForEvent(eventId);
    }

    @PostMapping("/inter-pod-card-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public InterPodCardDefinitionResponse createDefinition(
            @Valid @RequestBody CreateInterPodCardDefinitionRequest request) {
        return interPodCardDefinitionService.createDefinition(request);
    }

    @PutMapping("/inter-pod-card-definitions/{definitionId}")
    public InterPodCardDefinitionResponse updateDefinition(
            @PathVariable Long definitionId,
            @Valid @RequestBody UpdateInterPodCardDefinitionRequest request) {
        return interPodCardDefinitionService.updateDefinition(definitionId, request);
    }

    @DeleteMapping("/inter-pod-card-definitions/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDefinition(@PathVariable Long definitionId) {
        interPodCardDefinitionService.deleteDefinition(definitionId);
    }
}
