package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EventPlayerMetricsService {

    private final EventGroupRepository eventGroupRepository;
    private final EventRepository eventRepository;
    private final GameStateRepository gameStateRepository;
    private final VillainFormulaService villainFormulaService;

    public EventPlayerMetricsService(EventGroupRepository eventGroupRepository,
                                     EventRepository eventRepository,
                                     GameStateRepository gameStateRepository,
                                     VillainFormulaService villainFormulaService) {
        this.eventGroupRepository = eventGroupRepository;
        this.eventRepository = eventRepository;
        this.gameStateRepository = gameStateRepository;
        this.villainFormulaService = villainFormulaService;
    }

    public int synchronizePlayerMetrics(Event event) {
        int totalPlayers = Math.max(0, eventGroupRepository.sumPlayerCountByEventId(event.getId()));

        if (!Objects.equals(event.getConfiguredPlayerCount(), totalPlayers)) {
            event.setConfiguredPlayerCount(totalPlayers);
            eventRepository.save(event);
        }

        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.READY) {
            return totalPlayers;
        }

        gameStateRepository.findByEventId(event.getId()).ifPresent(gameState -> syncVillainHp(event, gameState, totalPlayers));
        return totalPlayers;
    }

    private void syncVillainHp(Event event, GameState gameState, int totalPlayers) {
        int villainHpMax = villainFormulaService.computeVillainHp(
                event.getVillainHpRuleTypeSnapshot(),
                event.getVillainHpRuleValueSnapshot(),
                totalPlayers
        );

        if (Objects.equals(gameState.getVillainHpMax(), villainHpMax)
                && Objects.equals(gameState.getVillainHpCurrent(), villainHpMax)) {
            return;
        }

        gameState.setVillainHpMax(villainHpMax);
        gameState.setVillainHpCurrent(villainHpMax);
        gameStateRepository.save(gameState);
    }
}