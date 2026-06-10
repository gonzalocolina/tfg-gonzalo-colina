package com.tfg.gonzalo.backend.dto.event;



import com.tfg.gonzalo.backend.model.enums.InterPodCardStatus;

import jakarta.validation.constraints.Min;

import jakarta.validation.constraints.NotNull;



public record CreateInterPodCardRequest(

        Long sourceGroupId,

        @NotNull Long interPodCardDefinitionId,

        @Min(1) Integer metricMax,

        InterPodCardStatus status

) {

}

