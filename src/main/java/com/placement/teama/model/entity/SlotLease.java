package com.placement.teama.model.entity;

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
public class SlotLease {
    private String leaseId;
    private String slotId;
    private String holderId;
    private Instant createdAt;
    private Instant expiresAt;
    private LeaseStatus status;
}