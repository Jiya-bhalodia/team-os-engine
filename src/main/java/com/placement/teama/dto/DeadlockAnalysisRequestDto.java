package com.placement.teama.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DeadlockAnalysisRequestDto {
    private Map<String, List<String>> waitGraph;
}