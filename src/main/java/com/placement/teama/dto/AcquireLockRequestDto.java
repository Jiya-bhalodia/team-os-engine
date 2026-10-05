package com.placement.teama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcquireLockRequestDto {
    @NotBlank private String slotId;
    @NotBlank private String studentId;
    private int ttlSeconds = 300;
}