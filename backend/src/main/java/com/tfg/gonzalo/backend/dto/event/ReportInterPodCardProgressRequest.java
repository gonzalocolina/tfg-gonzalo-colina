package com.tfg.gonzalo.backend.dto.event;



import jakarta.validation.constraints.Min;



public record ReportInterPodCardProgressRequest(

        @Min(1) Integer amount

) {

}

