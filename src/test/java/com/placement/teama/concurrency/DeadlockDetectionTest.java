package com.placement.teama.concurrency;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DeadlockDetectionTest {

    @Test
    @DisplayName("Verify seeded directed deadlock cycle: A -> B -> C -> A")
    void testSeededDeadlockDetection() {

        WaitForGraph wfg = new WaitForGraph();

        wfg.addEdge("StudentA", "StudentB");
        wfg.addEdge("StudentB", "StudentC");
        wfg.addEdge("StudentC", "StudentA");

        List<String> cycle = wfg.detectCycle();

        // A cycle must be detected
        Assertions.assertFalse(
                cycle.isEmpty(),
                "Failed to detect seeded deadlock cycle!"
        );

        // The detected cycle must contain all three students
        Assertions.assertTrue(
                cycle.contains("StudentA"),
                "Cycle should contain StudentA"
        );

        Assertions.assertTrue(
                cycle.contains("StudentB"),
                "Cycle should contain StudentB"
        );

        Assertions.assertTrue(
                cycle.contains("StudentC"),
                "Cycle should contain StudentC"
        );

        // First and last node should be the same
        Assertions.assertEquals(
                cycle.get(0),
                cycle.get(cycle.size() - 1),
                "Cycle should start and end at the same node"
        );
    }
}