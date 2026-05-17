package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.event.CreateInterPodCardRequest;
import com.tfg.gonzalo.backend.dto.event.InterPodCardResponse;
import com.tfg.gonzalo.backend.dto.event.ReportInterPodCardDamageRequest;
import com.tfg.gonzalo.backend.dto.event.ReportInterPodCardThreatRequest;
import com.tfg.gonzalo.backend.dto.event.UpdateInterPodCardStatusRequest;
import com.tfg.gonzalo.backend.service.InterPodCardService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/events/{eventId}/inter-pod-cards")
public class InterPodCardController {

    private final InterPodCardService interPodCardService;

    public InterPodCardController(InterPodCardService interPodCardService) {
        this.interPodCardService = interPodCardService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public InterPodCardResponse createCard(@PathVariable Long eventId,
                                           @Valid @RequestPart("payload") CreateInterPodCardRequest request,
                                           @RequestPart(value = "image", required = false) MultipartFile image) {
        return interPodCardService.createCard(eventId, request, image);
    }

    @GetMapping
    public List<InterPodCardResponse> listCards(@PathVariable Long eventId,
                                                @RequestParam(required = false) Long podId) {
        return interPodCardService.listCards(eventId, podId);
    }

    @PostMapping("/{cardId}/damage")
    public InterPodCardResponse reportDamage(@PathVariable Long eventId,
                                             @PathVariable Long cardId,
                                             @Valid @RequestBody ReportInterPodCardDamageRequest request) {
        return interPodCardService.reportDamage(eventId, cardId, request);
    }

    @PostMapping("/{cardId}/threat")
    public InterPodCardResponse reportThreat(@PathVariable Long eventId,
                                             @PathVariable Long cardId,
                                             @Valid @RequestBody ReportInterPodCardThreatRequest request) {
        return interPodCardService.reportThreat(eventId, cardId, request);
    }

    @PatchMapping("/{cardId}/status")
    public InterPodCardResponse updateStatus(@PathVariable Long eventId,
                                             @PathVariable Long cardId,
                                             @Valid @RequestBody UpdateInterPodCardStatusRequest request) {
        return interPodCardService.updateStatus(eventId, cardId, request);
    }

    @DeleteMapping("/{cardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCard(@PathVariable Long eventId,
                           @PathVariable Long cardId) {
        interPodCardService.deleteCard(eventId, cardId);
    }

    @GetMapping("/{cardId}/image")
    public ResponseEntity<Resource> getImage(@PathVariable Long eventId,
                                             @PathVariable Long cardId) {
        InterPodCardService.CardImagePayload payload = interPodCardService.getImage(eventId, cardId);
        MediaType contentType = payload.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(payload.contentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .body(payload.resource());
    }
}