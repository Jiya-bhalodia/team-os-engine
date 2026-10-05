package com.placement.teama.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSnapshot {
    private String studentId;
    private double cgpa;
    private int backlogs;
    private String branch;
    private double attendancePct;
    private List<String> skills;
}