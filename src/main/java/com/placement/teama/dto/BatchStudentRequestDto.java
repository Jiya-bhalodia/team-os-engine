package com.placement.teama.dto;

import com.placement.teama.model.entity.StudentSnapshot;
import lombok.Data;

@Data
public class BatchStudentRequestDto {
    private StudentSnapshot student;
    private int priority = 1;
    private String slotId;
    private Integer slotLeaseTtlSeconds = 300;
}
