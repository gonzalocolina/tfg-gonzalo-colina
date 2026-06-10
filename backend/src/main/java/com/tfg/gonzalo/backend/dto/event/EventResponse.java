package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.dto.interpod.InterPodCardDefinitionResponse;
import com.tfg.gonzalo.backend.model.enums.CoOrganizerResponsibility;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Configuración y metadata del evento. El estado vivo de la partida
 * (HP, amenaza, timer, resultado) se obtiene del snapshot de game-state.
 */
public record EventResponse(
        Long id,
        String name,
        Integer configuredPlayerCount,
        Integer timeLimitMinutes,
        EventStatus status,
        String eventCode,
        String organizerUsername,
        Long villainDefinitionId,
        String villainName,
        VillainDefinitionType villainType,
        List<InterPodCardDefinitionResponse> selectedInterPodCardDefinitions,
        VillainRuleType villainHpRuleType,
        Integer villainHpRuleValue,
        VillainRuleType villainThreatRuleType,
        Integer villainThreatRuleValue,
        boolean currentUserIsOwner,
        Set<CoOrganizerResponsibility> currentUserResponsibilities,
        Instant createdAt,
        Instant updatedAt
) {
}
