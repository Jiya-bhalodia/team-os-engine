package com.placement.teama.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.placement.teama.model.enums.LeaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SlotLease {
    @JsonAlias("leaseId")
    private String leaseId;
    @JsonAlias("slotId")
    private String slotId;
    @JsonAlias("holderId")
    private String holderId;
    @JsonAlias("createdAt")
    private Instant createdAt;
    @JsonAlias("expiresAt")
    private Instant expiresAt;
    private LeaseStatus status;
}
