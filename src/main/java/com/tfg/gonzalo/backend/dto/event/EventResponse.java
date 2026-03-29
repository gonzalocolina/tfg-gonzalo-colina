package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import com.tfg.gonzalo.backend.model.enums.VillainRuleType;
import java.time.Instant;

public record EventResponse(
        Long id,
        String name,
        Integer configuredPlayerCount,
        Integer timeLimitMinutes,
        EventStatus status,
        EventResult result,
        String eventCode,
        String organizerUsername,
        Long villainDefinitionId,
        String villainName,
        VillainDefinitionType villainType,
        VillainRuleType villainHpRuleType,
        Integer villainHpRuleValue,
        VillainRuleType villainThreatRuleType,
        Integer villainThreatRuleValue,
        Integer villainHpCurrent,
        Integer villainHpMax,
        Integer schemeThreatCurrent,
        Integer schemeThreatTarget,
        Integer timerRemainingSeconds,
        TimerStatus timerStatus,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
}