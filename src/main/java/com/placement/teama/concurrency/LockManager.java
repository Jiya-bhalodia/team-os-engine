package com.placement.teama.concurrency;

import com.placement.teama.model.entity.InterviewSlot;
import com.placement.teama.model.entity.SlotLease;
import com.placement.teama.model.enums.LeaseStatus;
import com.placement.teama.model.enums.SlotState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OS Synchronization Engine.
 
 * Provides:
 * - Fair ReentrantLock for mutual exclusion
 * - Semaphore for exclusive slot access
 * - Lease-based locking with TTL
 * - Automatic lease expiry
 * - Race-condition prevention
 */
@Component
public class LockManager {

    private final Map<String, ReentrantLock> slotMutexes =
            new ConcurrentHashMap<>();

    private final Map<String, Semaphore> slotSemaphores =
            new ConcurrentHashMap<>();

    private final Map<String, InterviewSlot> slotRegistry =
            new ConcurrentHashMap<>();

    private final Map<String, SlotLease> activeLeases =
            new ConcurrentHashMap<>();

    /** Represents actual contention observed during slot acquisition. */
    private final WaitForGraph waitForGraph = new WaitForGraph();


    /**
     * Register an interview slot.
     */
    public synchronized void registerSlot(InterviewSlot slot) {

        if (slot == null ||
                slot.getSlotId() == null ||
                slot.getSlotId().isBlank()) {

            throw new IllegalArgumentException(
                    "Valid slot is required");
        }

        if (slot.getCapacity() <= 0) {
            throw new IllegalArgumentException(
                    "Slot capacity must be greater than 0");
        }

        // Do not replace an already registered slot.
        if (slotRegistry.containsKey(slot.getSlotId())) {
            throw new IllegalArgumentException(
                    "Slot is already registered: " + slot.getSlotId());
        }

        slotRegistry.put(slot.getSlotId(), slot);

        // Fair lock prevents indefinite preference for some threads.
        slotMutexes.put(
                slot.getSlotId(),
                new ReentrantLock(true)
        );

        /*
         * Team A requirement is exclusive slot allocation.
         * Therefore only ONE student can hold the slot at a time.
         */
        slotSemaphores.put(
                slot.getSlotId(),
                new Semaphore(1, true)
        );
    }


    /**
     * Acquire an exclusive interview-slot lease.
     */
    public SlotLease acquireSlotLock(
            String slotId,
            String studentId,
            int ttlSeconds) throws InterruptedException {

        if (slotId == null || slotId.isBlank()) {
            throw new IllegalArgumentException(
                    "slotId is required");
        }

        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException(
                    "studentId is required");
        }

        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "TTL must be greater than 0 seconds");
        }

        /*
         * The slot must already be registered.
         */
        InterviewSlot slot = slotRegistry.get(slotId);

        if (slot == null) {
            throw new IllegalArgumentException(
                    "Slot is not registered: " + slotId);
        }

        ReentrantLock mutex = slotMutexes.get(slotId);
        Semaphore semaphore = slotSemaphores.get(slotId);

        /*
         * First acquire the semaphore.
         * Only one student can obtain the exclusive-slot permit.
         */
        boolean acquired =
                semaphore.tryAcquire(
                        
        
                );

        if (!acquired) {
            String holder = slot.getCurrentHolder();
            if (holder != null) {
                waitForGraph.addEdge(studentId, holder);
            }
            return null;
        }

        /*
         * Protect slot state using the mutex.
         */
        mutex.lock();

        try {

            /*
             * Remove expired leases before checking availability.
             */
            housekeepLeases(slotId);

            /*
             * Check slot state again after acquiring the mutex.
             */
            if (slot.getState() != SlotState.AVAILABLE) {

                semaphore.release();
                return null;
            }

            Instant now = Instant.now();

            SlotLease lease =
                    SlotLease.builder()
                            .leaseId(
                                    "LEASE-" +
                                    UUID.randomUUID()
                                            .toString()
                                            .substring(0, 8)
                            )
                            .slotId(slotId)
                            .holderId(studentId)
                            .createdAt(now)
                            .expiresAt(
                                    now.plusSeconds(ttlSeconds)
                            )
                            .status(LeaseStatus.ACTIVE)
                            .build();

            /*
             * Mark slot as locked BEFORE returning the lease.
             */
            slot.setState(SlotState.LOCKED);
            slot.setCurrentHolder(studentId);

            activeLeases.put(
                    lease.getLeaseId(),
                    lease
            );
            waitForGraph.removeEdge(studentId, slot.getCurrentHolder());

            return lease;

        } finally {
            mutex.unlock();
        }
    }

    /** A small, deterministic deadlock signal used by the lifecycle worker and telemetry. */
    public String deadlockSuffix() {
        List<String> cycle = waitForGraph.detectCycle();
        return cycle.isEmpty() ? "" : "; deadlock cycle detected: " + cycle;
    }

    /** Creates and recovers a controlled wait-for cycle without blocking application threads. */
    public synchronized List<String> runControlledDeadlockDemo(String firstRequest, String secondRequest) {
        waitForGraph.addEdge(firstRequest, secondRequest);
        waitForGraph.addEdge(secondRequest, firstRequest);
        List<String> cycle = waitForGraph.detectCycle();
        waitForGraph.removeEdge(firstRequest, secondRequest);
        waitForGraph.removeEdge(secondRequest, firstRequest);
        return cycle;
    }


    /**
     * Release an active slot lease.
     */
    public boolean releaseSlotLock(String leaseId) {

        if (leaseId == null || leaseId.isBlank()) {
            return false;
        }

        SlotLease lease =
                activeLeases.get(leaseId);

        if (lease == null) {
            return false;
        }

        ReentrantLock mutex =
                slotMutexes.get(lease.getSlotId());

        if (mutex == null) {
            return false;
        }

        mutex.lock();

        try {

            /*
             * Check again while holding the mutex.
             * This prevents release and expiry from
             * modifying the same lease simultaneously.
             */
            if (lease.getStatus() != LeaseStatus.ACTIVE) {
                return false;
            }

            lease.setStatus(LeaseStatus.RELEASED);

            InterviewSlot slot =
                    slotRegistry.get(lease.getSlotId());

            if (slot != null &&
                    lease.getHolderId().equals(
                            slot.getCurrentHolder())) {

                slot.setState(SlotState.AVAILABLE);
                slot.setCurrentHolder(null);
            }

            /*
             * Return the semaphore permit.
             */
            Semaphore semaphore =
                    slotSemaphores.get(lease.getSlotId());

            if (semaphore != null) {
                semaphore.release();
            }

            return true;

        } finally {
            mutex.unlock();
        }
    }


    /** Finalizes an active runtime lease after Team C commits authoritative state. */
    public boolean commitSlotLease(String leaseId) {
        if (leaseId == null || leaseId.isBlank()) return false;
        SlotLease lease = activeLeases.get(leaseId);
        if (lease == null) return false;
        ReentrantLock mutex = slotMutexes.get(lease.getSlotId());
        if (mutex == null) return false;
        mutex.lock();
        try {
            if (lease.getStatus() != LeaseStatus.ACTIVE) return false;
            lease.setStatus(LeaseStatus.COMMITTED);
            InterviewSlot slot = slotRegistry.get(lease.getSlotId());
            if (slot != null) slot.setState(SlotState.BOOKED);
            return true;
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Automatically expire leases whose TTL has ended.
     *
     * Runs every second.
     */
    @Scheduled(fixedDelay = 1000)
    public void cleanupExpiredLeases() {

        for (InterviewSlot slot : slotRegistry.values()) {

            ReentrantLock mutex =
                    slotMutexes.get(slot.getSlotId());

            if (mutex == null) {
                continue;
            }

            mutex.lock();

            try {
                housekeepLeases(slot.getSlotId());
            } finally {
                mutex.unlock();
            }
        }
    }


    /**
     * Expire active leases for a particular slot.
     *
     * This method must be called while the slot mutex is held.
     */
    private void housekeepLeases(String slotId) {

        Instant now = Instant.now();

        for (SlotLease lease : activeLeases.values()) {

            if (!slotId.equals(lease.getSlotId())) {
                continue;
            }

            if (lease.getStatus() != LeaseStatus.ACTIVE) {
                continue;
            }

            if (now.isBefore(lease.getExpiresAt())) {
                continue;
            }

            /*
             * Expire the lease.
             */
            lease.setStatus(LeaseStatus.EXPIRED);

            InterviewSlot slot =
                    slotRegistry.get(slotId);

            if (slot != null &&
                    lease.getHolderId().equals(
                            slot.getCurrentHolder())) {

                slot.setState(SlotState.AVAILABLE);
                slot.setCurrentHolder(null);
            }

            /*
             * Return the semaphore permit.
             */
            Semaphore semaphore =
                    slotSemaphores.get(slotId);

            if (semaphore != null) {
                semaphore.release();
            }
        }
    }
}
