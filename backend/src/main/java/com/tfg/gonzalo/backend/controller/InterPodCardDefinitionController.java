package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.interpod.CreateInterPodCardDefinitionRequest;
import com.tfg.gonzalo.backend.dto.interpod.InterPodCardDefinitionResponse;
import com.tfg.gonzalo.backend.dto.interpod.UpdateInterPodCardDefinitionRequest;
import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import com.tfg.gonzalo.backend.service.InterPodCardDefinitionService;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/events/{eventId}/inter-pod-card-definitions/reportable")
    public List<InterPodCardDefinitionResponse> listReportableEventDefinitions(@PathVariable Long eventId) {
        return interPodCardDefinitionService.listReportableDefinitionsForAuthenticatedGroup(eventId);
    }

    @PostMapping(value = "/inter-pod-card-definitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public InterPodCardDefinitionResponse createDefinition(
            @RequestParam("name") String name,
            @RequestParam("cardCategory") InterPodCardCategory cardCategory,
            @RequestParam(value = "defaultMetricMax", required = false) Integer defaultMetricMax,
            @RequestParam(value = "ruleType", required = false) VillainRuleType ruleType,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        var request = new CreateInterPodCardDefinitionRequest(
                name,
                cardCategory,
                defaultMetricMax,
                ruleType,
                imageUrl
        );
        return interPodCardDefinitionService.createDefinition(request, image);
    }

    @PutMapping(value = "/inter-pod-card-definitions/{definitionId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InterPodCardDefinitionResponse updateDefinition(
            @PathVariable Long definitionId,
            @RequestParam("name") String name,
            @RequestParam("cardCategory") InterPodCardCategory cardCategory,
            @RequestParam(value = "defaultMetricMax", required = false) Integer defaultMetricMax,
            @RequestParam(value = "ruleType", required = false) VillainRuleType ruleType,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        var request = new UpdateInterPodCardDefinitionRequest(
                name,
                cardCategory,
                defaultMetricMax,
                ruleType,
                imageUrl
        );
        return interPodCardDefinitionService.updateDefinition(definitionId, request, image);
    }

    @DeleteMapping("/inter-pod-card-definitions/{definitionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDefinition(@PathVariable Long definitionId) {
        interPodCardDefinitionService.deleteDefinition(definitionId);
    }

    @GetMapping("/inter-pod-card-definitions/{definitionId}/image")
    public ResponseEntity<Resource> getImage(@PathVariable Long definitionId) {
        InterPodCardDefinitionService.DefinitionImagePayload payload =
                interPodCardDefinitionService.getImage(definitionId);
        MediaType contentType = payload.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(payload.contentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(payload.resource());
    }
}
