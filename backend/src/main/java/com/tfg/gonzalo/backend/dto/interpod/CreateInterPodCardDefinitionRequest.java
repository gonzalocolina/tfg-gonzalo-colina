package com.tfg.gonzalo.backend.dto.interpod;



import com.tfg.gonzalo.backend.model.enums.InterPodCardCategory;

import com.tfg.gonzalo.backend.model.enums.VillainRuleType;

import jakarta.validation.constraints.Min;

import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Size;



public record CreateInterPodCardDefinitionRequest(

        @NotBlank @Size(max = 120) String name,

        InterPodCardCategory cardCategory,

        @Min(0) Integer defaultMetricMax,

        VillainRuleType ruleType,

        @Size(max = 500) String imageUrl

) {

}

