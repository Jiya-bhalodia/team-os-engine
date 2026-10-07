package com.placement.teama.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DeadlockAnalysisRequestDto {
    @JsonAlias("waitGraph")
    private Map<String, List<String>> waitGraph;
}
