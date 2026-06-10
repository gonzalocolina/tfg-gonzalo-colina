package com.tfg.gonzalo.backend.service;

import com.tfg.gonzalo.backend.model.Event;
import com.tfg.gonzalo.backend.model.EventVillain;
import com.tfg.gonzalo.backend.repository.EventGroupRepository;
import org.springframework.stereotype.Service;

/**
 * Calcula el estado derivado de un evento que ya no se persiste en la tabla events:
 * número de jugadores, HP máximo/actual del villano y amenaza objetivo.
 */
@Service
public class EventStateCalculator {

    private final EventGroupRepository eventGroupRepository;
    private final VillainFormulaService villainFormulaService;

    public EventStateCalculator(EventGroupRepository eventGroupRepository,
                                VillainFormulaService villainFormulaService) {
        this.eventGroupRepository = eventGroupRepository;
        this.villainFormulaService = villainFormulaService;
    }

    public int playerCount(Event event) {
        if (event.getId() == null) {
            return 0;
        }
        Integer sum = eventGroupRepository.sumPlayerCountByEventId(event.getId());
        return sum == null ? 0 : Math.max(0, sum);
    }

    public int groupCount(Event event) {
        if (event.getId() == null) {
            return 0;
        }
        return (int) eventGroupRepository.countByEventId(event.getId());
    }

    public int villainHpMax(Event event) {
        EventVillain villain = event.getVillain();
        if (villain == null) {
            return 0;
        }
        return villainFormulaService.computeVillainHp(
                villain.getHpRuleType(),
                villain.getHpRuleValue(),
                playerCount(event)
        );
    }

    public int villainHpCurrent(Event event) {
        return villainHpCurrent(event, villainHpMax(event));
    }

    public int villainHpCurrent(Event event, int villainHpMax) {
        int damageTaken = event.getVillainDamageTaken() == null ? 0 : event.getVillainDamageTaken();
        return Math.max(0, villainHpMax - damageTaken);
    }

    public int threatTarget(Event event) {
        EventVillain villain = event.getVillain();
        if (villain == null) {
            return 0;
        }
        return villainFormulaService.computeThreatTarget(
                villain.getThreatRuleType(),
                villain.getThreatRuleValue(),
                groupCount(event)
        );
    }
}
