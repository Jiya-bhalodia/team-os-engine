package com.placement.teama.concurrency;

import com.placement.teama.model.entity.InterviewSlot;
import com.placement.teama.model.entity.SlotLease;
import com.placement.teama.model.enums.SlotState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LockManagerConcurrencyTest {

    @Test
    @DisplayName("Verify no duplicate allocations for 10000 concurrent attempts")
    void testConcurrentSlotAllocationSafety() throws InterruptedException {

        LockManager lockManager = new LockManager();

        String slotId = "SLOT-EXCLUSIVE-101";

        lockManager.registerSlot(
                InterviewSlot.builder()
                        .slotId(slotId)
                        .driveId("DRIVE-AMAZON")
                        .date("2026-10-15")
                        .startTime("10:00")
                        .endTime("10:30")
                        .capacity(1)
                        .state(SlotState.AVAILABLE)
                        .build()
        );

        int threadCount = 10000;

        ExecutorService executor =
                Executors.newFixedThreadPool(100);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<SlotLease> grantedLeases =
                Collections.synchronizedList(
                        new ArrayList<>()
                );

        for (int i = 0; i < threadCount; i++) {

            final String studentId =
                    "STUDENT-" + i;

            executor.submit(() -> {

                try {

                    startLatch.await();

                    SlotLease lease =
                            lockManager.acquireSlotLock(
                                    slotId,
                                    studentId,
                                    60
                            );

                    if (lease != null) {
                        grantedLeases.add(lease);
                    }

                } catch (Exception e) {
                    // Failed attempts are expected.
                }
            });
        }

        // Start all competing attempts.
        startLatch.countDown();

        executor.shutdown();

        boolean completed =
                executor.awaitTermination(
                        10,
                        TimeUnit.SECONDS
                );

        Assertions.assertTrue(
                completed,
                "Test did not finish within timeout"
        );

        // Only ONE student can hold an exclusive slot.
        Assertions.assertEquals(
                1,
                grantedLeases.size(),
                "Safety violation! Duplicate leases issued for exclusive slot"
        );
    }
}