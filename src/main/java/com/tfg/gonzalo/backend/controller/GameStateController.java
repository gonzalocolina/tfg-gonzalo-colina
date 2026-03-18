package com.tfg.gonzalo.backend.controller;

import com.tfg.gonzalo.backend.dto.gamestate.GameStateOperationResponse;
import com.tfg.gonzalo.backend.dto.gamestate.GameStateSnapshotResponse;
import com.tfg.gonzalo.backend.dto.gamestate.ManualThreatUpdateRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ManualVillainHpUpdateRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ReportThreatRequest;
import com.tfg.gonzalo.backend.dto.gamestate.ReportVillainDamageRequest;
import com.tfg.gonzalo.backend.dto.gamestate.TimerAdjustRequest;
import com.tfg.gonzalo.backend.service.GameStateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/game-state")
public class GameStateController {

    private final GameStateService gameStateService;

    public GameStateController(GameStateService gameStateService) {
        this.gameStateService = gameStateService;
    }

    @GetMapping
    public GameStateSnapshotResponse getSnapshot(@PathVariable Long eventId) {
        return gameStateService.getSnapshot(eventId);
    }

    @PostMapping("/villain-damage")
    public GameStateOperationResponse reportVillainDamage(@PathVariable Long eventId,
                                                          @Valid @RequestBody ReportVillainDamageRequest request) {
        return gameStateService.reportVillainDamage(eventId, request);
    }

    @PostMapping("/threat")
    public GameStateOperationResponse reportThreat(@PathVariable Long eventId,
                                                   @Valid @RequestBody ReportThreatRequest request) {
        return gameStateService.reportThreat(eventId, request);
    }

    @PatchMapping("/villain-hp")
    public GameStateOperationResponse updateVillainHp(@PathVariable Long eventId,
                                                      @Valid @RequestBody ManualVillainHpUpdateRequest request) {
        return gameStateService.updateVillainHp(eventId, request);
    }

    @PatchMapping("/threat")
    public GameStateOperationResponse updateThreat(@PathVariable Long eventId,
                                                   @Valid @RequestBody ManualThreatUpdateRequest request) {
        return gameStateService.updateThreat(eventId, request);
    }

    @PostMapping("/start")
    public GameStateOperationResponse startGame(@PathVariable Long eventId) {
        return gameStateService.startGame(eventId);
    }

    @PostMapping("/timer/start")
    public GameStateOperationResponse startTimer(@PathVariable Long eventId) {
        return gameStateService.startTimer(eventId);
    }

    @PostMapping("/timer/pause")
    public GameStateOperationResponse pauseTimer(@PathVariable Long eventId) {
        return gameStateService.pauseTimer(eventId);
    }

    @PostMapping("/timer/resume")
    public GameStateOperationResponse resumeTimer(@PathVariable Long eventId) {
        return gameStateService.resumeTimer(eventId);
    }

    @PostMapping("/timer/adjust")
    public GameStateOperationResponse adjustTimer(@PathVariable Long eventId,
                                                  @Valid @RequestBody TimerAdjustRequest request) {
        return gameStateService.adjustTimer(eventId, request);
    }
}