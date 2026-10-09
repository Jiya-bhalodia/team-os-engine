package com.placement.teama.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StudentSnapshot {
    @JsonAlias("studentId")
    private String studentId;
    private Double cgpa;
    private Integer backlogs;
    private String branch;
    @JsonAlias("attendancePct")
    private Double attendancePct;
    private List<String> skills;
}
