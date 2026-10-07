package com.placement.teama.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.placement.teama.model.enums.SlotState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InterviewSlot {
    @JsonAlias("slotId")
    private String slotId;
    @JsonAlias("driveId")
    private String driveId;
    private String date;
    @JsonAlias("startTime")
    private String startTime;
    @JsonAlias("endTime")
    private String endTime;
    @Builder.Default
    private int capacity = 1;
    @JsonAlias("currentHolder")
    private String currentHolder;
    @Builder.Default
    private SlotState state = SlotState.AVAILABLE;
}
