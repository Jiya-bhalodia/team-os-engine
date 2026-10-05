package com.placement.teama.controller;

import com.placement.teama.concurrency.LockManager;
import com.placement.teama.concurrency.WaitForGraph;
import com.placement.teama.dto.*;
import com.placement.teama.model.entity.SlotLease;
import com.placement.teama.telemetry.TelemetryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class LockController {

    private final LockManager lockManager;
    private final TelemetryService telemetryService;

    public LockController(
            LockManager lockManager,
            TelemetryService telemetryService) {

        this.lockManager = lockManager;
        this.telemetryService = telemetryService;
    }

    // A3 - Acquire interview slot lock
    @PostMapping("/internal/v1/locks/acquire")
    public ResponseEntity<ApiResponse<SlotLease>> acquireLock(

            @Valid @RequestBody AcquireLockRequestDto body,

            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false) String corrId) {

        if (corrId == null || corrId.isBlank()) {
            corrId = "corr-" + UUID.randomUUID();
        }

        try {

            telemetryService.recordLockAttempt();

            SlotLease lease = lockManager.acquireSlotLock(
                    body.getSlotId(),
                    body.getStudentId(),
                    body.getTtlSeconds()
            );

            if (lease == null) {

                telemetryService.recordLockConflict();

                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(
                                "SLOT_LOCK_FAILED",
                                "Slot is already locked or unavailable",
                                corrId
                        ));
            }

            return ResponseEntity.ok(
                    ApiResponse.success(lease, corrId));

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            telemetryService.recordLockConflict();

            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(
                            "LOCK_WAIT_INTERRUPTED",
                            "Lock acquisition was interrupted",
                            corrId
                    ));
        }
    }


    // A3 - Release interview slot lock
    @DeleteMapping("/internal/v1/locks/{leaseId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> releaseLock(

            @PathVariable String leaseId,

            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false) String corrId) {

        if (corrId == null || corrId.isBlank()) {
            corrId = "corr-" + UUID.randomUUID();
        }

        boolean released =
                lockManager.releaseSlotLock(leaseId);

        if (!released) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(
                            "LEASE_RELEASE_FAILED",
                            "Invalid or already released lease",
                            corrId
                    ));
        }

        return ResponseEntity.ok(
                ApiResponse.success(
                        Map.of("released", true),
                        corrId
                ));
    }


    /** Called by Team C only after its authoritative commit succeeds. */
    @PostMapping("/internal/v1/locks/{leaseId}/commit")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commitLock(
            @PathVariable String leaseId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String corrId) {
        if (corrId == null || corrId.isBlank()) corrId = "corr-" + UUID.randomUUID();
        if (!lockManager.commitSlotLease(leaseId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("LEASE_COMMIT_FAILED",
                            "Invalid, expired, released, or committed lease", corrId));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of("committed", true, "leaseId", leaseId), corrId));
    }


    // A3 - Analyse deadlock
    @PostMapping("/internal/v1/deadlocks/analyse")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyseDeadlock(

            @RequestBody(
                    required = false)
            DeadlockAnalysisRequestDto body,

            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false) String corrId) {

        if (corrId == null || corrId.isBlank()) {
            corrId = "corr-" + UUID.randomUUID();
        }

        WaitForGraph waitForGraph =
                new WaitForGraph();

        if (body != null &&
                body.getWaitGraph() != null) {

            body.getWaitGraph().forEach(
                    (node, edges) -> {

                        if (edges != null) {
                            edges.forEach(
                                    edge -> waitForGraph.addEdge(
                                            node,
                                            edge
                                    )
                            );
                        }
                    }
            );
        }

        List<String> cycle =
                waitForGraph.detectCycle();

        boolean detected =
                !cycle.isEmpty();

        if (detected) {
            telemetryService.recordDeadlock();
        }

        Map<String, Object> data = Map.of(
                "deadlock_detected", detected,
                "deadlock_cycle", cycle,
                "action",
                detected
                        ? "RELEASE_RESOURCE_AND_RETRY"
                        : "NO_ACTION_REQUIRED"
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        data,
                        corrId
                ));
    }
}
