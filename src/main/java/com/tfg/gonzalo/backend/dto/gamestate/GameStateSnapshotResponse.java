package com.tfg.gonzalo.backend.dto.gamestate;

import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.EventStatus;
import com.tfg.gonzalo.backend.model.enums.TimerStatus;
import com.tfg.gonzalo.backend.model.enums.VillainDefinitionType;
import java.time.Instant;

public record GameStateSnapshotResponse(
        Long eventId,
        EventStatus eventStatus,
        EventResult eventResult,
        String villainName,
        VillainDefinitionType villainType,
        Integer villainHpCurrent,
        Integer villainHpMax,
        Integer schemeThreatCurrent,
        Integer schemeThreatTarget,
        Integer timerRemainingSeconds,
        TimerStatus timerStatus,
        Integer totalReportedVillainDamage,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt,
        FinalSummaryResponse finalSummary
) {
}