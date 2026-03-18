package com.tfg.gonzalo.backend.dto.gamestate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record TimerAdjustRequest(
        @Min(-300) @Max(300) Integer minutesDelta
) {
}