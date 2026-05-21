package com.tfg.gonzalo.backend.dto.event;

import com.tfg.gonzalo.backend.model.enums.EventActionType;
import java.time.Instant;

public record EventLogEntryResponse(
        Long id,
        Instant timestamp,
        String actor,
        String groupIdentifier,
        EventActionType actionType,
        Long revertedLogId,
        String previousValue,
        String newValue,
        String notes
) {
}
