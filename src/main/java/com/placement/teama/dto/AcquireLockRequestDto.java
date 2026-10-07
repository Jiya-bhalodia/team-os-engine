package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AcquireLockRequestDto {
    @JsonAlias("slotId")
    @NotBlank private String slotId;
    @JsonAlias("studentId")
    @NotBlank private String studentId;
    @JsonAlias("ttlSeconds")
    private int ttlSeconds = 300;
}
