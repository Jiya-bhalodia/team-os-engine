package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.placement.teama.model.entity.StudentSnapshot;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BatchStudentRequestDto {
    private StudentSnapshot student;
    private int priority = 1;
    @JsonAlias("slotId")
    private String slotId;
    @JsonAlias("slotLeaseTtlSeconds")
    private Integer slotLeaseTtlSeconds = 300;
}
