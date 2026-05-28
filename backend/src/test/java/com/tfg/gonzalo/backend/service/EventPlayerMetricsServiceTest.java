package com.tfg.gonzalo.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.GameState;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import com.tfg.gonzalo.backend.repository.GameStateRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventPlayerMetricsServiceTest {

    @Test
    void testUpdateDraftMetrics() {
        EventGroupRepository eventGroupRepository = mock(EventGroupRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        VillainFormulaService villainFormulaService = mock(VillainFormulaService.class);
        EventPlayerMetricsService service = new EventPlayerMetricsService(
                eventGroupRepository,
                eventRepository,
                gameStateRepository,
                villainFormulaService
        );

        Event event = buildEvent(42L, 2, EventStatus.DRAFT, VillainRuleType.MULTIPLIER, 10);
        GameState gameState = buildGameState(event, 20, 20);

        when(eventGroupRepository.sumPlayerCountByEventId(42L)).thenReturn(4);
        when(gameStateRepository.findByEventId(42L)).thenReturn(Optional.of(gameState));
        when(villainFormulaService.computeVillainHp(VillainRuleType.MULTIPLIER, 10, 4)).thenReturn(40);

        int totalPlayers = service.synchronizePlayerMetrics(event);

        assertEquals(4, totalPlayers);
        assertEquals(4, event.getConfiguredPlayerCount());
        assertEquals(40, gameState.getVillainHpMax());
        assertEquals(40, gameState.getVillainHpCurrent());
        verify(eventRepository).save(event);
        verify(gameStateRepository).save(gameState);
    }

    @Test
    void testUpdatePlayersInProgress() {
        EventGroupRepository eventGroupRepository = mock(EventGroupRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        VillainFormulaService villainFormulaService = mock(VillainFormulaService.class);
        EventPlayerMetricsService service = new EventPlayerMetricsService(
                eventGroupRepository,
                eventRepository,
                gameStateRepository,
                villainFormulaService
        );

        Event event = buildEvent(7L, 2, EventStatus.IN_PROGRESS, VillainRuleType.MULTIPLIER, 8);

        when(eventGroupRepository.sumPlayerCountByEventId(7L)).thenReturn(3);

        int totalPlayers = service.synchronizePlayerMetrics(event);

        assertEquals(3, totalPlayers);
        assertEquals(3, event.getConfiguredPlayerCount());
        verify(eventRepository).save(event);
        verify(gameStateRepository, never()).findByEventId(anyLong());
        verify(gameStateRepository, never()).save(org.mockito.ArgumentMatchers.any(GameState.class));
        verify(villainFormulaService, never()).computeVillainHp(org.mockito.ArgumentMatchers.any(),
         org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void testSkipPersistWhenMetricsAreSynced() {
        EventGroupRepository eventGroupRepository = mock(EventGroupRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        VillainFormulaService villainFormulaService = mock(VillainFormulaService.class);
        EventPlayerMetricsService service = new EventPlayerMetricsService(
                eventGroupRepository,
                eventRepository,
                gameStateRepository,
                villainFormulaService
        );

        Event event = buildEvent(99L, 3, EventStatus.READY, VillainRuleType.MULTIPLIER, 12);
        GameState gameState = buildGameState(event, 36, 36);

        when(eventGroupRepository.sumPlayerCountByEventId(99L)).thenReturn(3);
        when(gameStateRepository.findByEventId(99L)).thenReturn(Optional.of(gameState));
        when(villainFormulaService.computeVillainHp(VillainRuleType.MULTIPLIER, 12, 3)).thenReturn(36);

        int totalPlayers = service.synchronizePlayerMetrics(event);

        assertEquals(3, totalPlayers);
        verify(eventRepository, never()).save(event);
        verify(gameStateRepository, never()).save(gameState);
    }

    private Event buildEvent(Long eventId,
                             int configuredPlayerCount,
                             EventStatus status,
                             VillainRuleType hpRuleType,
                             int hpRuleValue) {
        Event event = new Event();
        event.setId(eventId);
        event.setConfiguredPlayerCount(configuredPlayerCount);
        event.setStatus(status);
        event.setVillainHpRuleTypeSnapshot(hpRuleType);
        event.setVillainHpRuleValueSnapshot(hpRuleValue);
        return event;
    }

    private GameState buildGameState(Event event, int villainHpMax, int villainHpCurrent) {
        GameState gameState = new GameState();
        gameState.setEvent(event);
        gameState.setVillainHpMax(villainHpMax);
        gameState.setVillainHpCurrent(villainHpCurrent);
        return gameState;
    }
}
