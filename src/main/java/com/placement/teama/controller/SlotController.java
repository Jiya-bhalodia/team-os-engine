package com.placement.teama.controller;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.dto.ApiResponse;
import com.placement.teama.model.entity.InterviewSlot;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class SlotController {

    private final LockManager lockManager;

    public SlotController(LockManager lockManager) {
        this.lockManager = lockManager;
    }

    @PostMapping("/internal/v1/slots")
    public ResponseEntity<ApiResponse<InterviewSlot>> registerSlot(
            @Valid @RequestBody InterviewSlot slot,
            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false) String corrId) {

        if (corrId == null || corrId.isBlank()) {
            corrId = "corr-" + UUID.randomUUID();
        }

        lockManager.registerSlot(slot);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(slot, corrId));
    }
}