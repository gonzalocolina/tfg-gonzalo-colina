package com.tfg.gonzalo.backend.dto.gamestate;

import com.tfg.gonzalo.backend.model.enums.EventResult;
import com.tfg.gonzalo.backend.model.enums.GameFinishCause;

public record FinalSummaryResponse(
        EventResult result,
        GameFinishCause cause,
        Long durationSeconds,
        Integer totalReportedVillainDamage,
        Integer finalThreat
) {
}