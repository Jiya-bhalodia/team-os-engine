package com.placement.teama.model.entity;

import com.placement.teama.model.enums.SlotState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSlot {
    private String slotId;
    private String driveId;
    private String date;
    private String startTime;
    private String endTime;
    @Builder.Default
    private int capacity = 1;
    private String currentHolder;
    @Builder.Default
    private SlotState state = SlotState.AVAILABLE;
}