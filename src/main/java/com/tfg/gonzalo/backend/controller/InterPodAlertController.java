package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.CreateInterPodAlertRequest;
import com.tfg.gonzalo.backend.dto.event.InterPodAlertResponse;
import com.tfg.gonzalo.backend.dto.event.UpdateInterPodAlertStatusRequest;
import com.tfg.gonzalo.backend.service.InterPodAlertService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/inter-pod-alerts")
public class InterPodAlertController {

    private final InterPodAlertService interPodAlertService;

    public InterPodAlertController(InterPodAlertService interPodAlertService) {
        this.interPodAlertService = interPodAlertService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterPodAlertResponse createAlert(@PathVariable Long eventId,
                                             @Valid @RequestBody CreateInterPodAlertRequest request) {
        return interPodAlertService.createAlert(eventId, request);
    }

    @PatchMapping("/{alertId}/status")
    public InterPodAlertResponse updateAlertStatus(@PathVariable Long eventId,
                                                   @PathVariable Long alertId,
                                                   @Valid @RequestBody UpdateInterPodAlertStatusRequest request) {
        return interPodAlertService.updateStatus(eventId, alertId, request);
    }

    @GetMapping
    public List<InterPodAlertResponse> listAlerts(@PathVariable Long eventId,
                                                  @RequestParam(required = false) Long podId) {
        return interPodAlertService.listAlerts(eventId, podId);
    }
}
