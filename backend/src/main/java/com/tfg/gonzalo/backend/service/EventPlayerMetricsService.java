package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import com.tfg.gonzalo.backend.repository.EventRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class EventPlayerMetricsService {

    private final EventGroupRepository eventGroupRepository;
    private final EventRepository eventRepository;
    private final VillainFormulaService villainFormulaService;

    public EventPlayerMetricsService(EventGroupRepository eventGroupRepository,
                                     EventRepository eventRepository,
                                     VillainFormulaService villainFormulaService) {
        this.eventGroupRepository = eventGroupRepository;
        this.eventRepository = eventRepository;
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

        syncVillainHp(event, totalPlayers);
        return totalPlayers;
    }

    private void syncVillainHp(Event event, int totalPlayers) {
        if (event.getVillain() == null) {
            return;
        }
        int villainHpMax = villainFormulaService.computeVillainHp(
                event.getVillain().getHpRuleType(),
                event.getVillain().getHpRuleValue(),
                totalPlayers
        );

        if (Objects.equals(event.getVillainHpMax(), villainHpMax)
                && Objects.equals(event.getVillainHpCurrent(), villainHpMax)) {
            return;
        }

        event.setVillainHpMax(villainHpMax);
        event.setVillainHpCurrent(villainHpMax);
        eventRepository.save(event);
    }
}