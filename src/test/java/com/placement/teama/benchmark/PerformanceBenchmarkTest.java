package com.placement.teama.benchmark;

import com.placement.teama.algorithm.rule.SequentialAndStrategy;
import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PerformanceBenchmarkTest {

    @Test
    @DisplayName("Verify decision latency p95 <= 200 ms for 1,000 requests")
    void testDecisionLatencyKpi() {

        SequentialAndStrategy engine =
                new SequentialAndStrategy();

        RuleSet ruleSet = RuleSet.builder()
                .version("v1")
                .rules(List.of(
                        new EligibilityRule(
                                "R1",
                                "min_cgpa",
                                7.5,
                                1.0
                        ),
                        new EligibilityRule(
                                "R2",
                                "max_backlogs",
                                2,
                                1.0
                        ),
                        new EligibilityRule(
                                "R3",
                                "allowed_branches",
                                List.of("CSE", "IT"),
                                1.0
                        )
                ))
                .build();

        StudentSnapshot student =
                StudentSnapshot.builder()
                        .studentId("STU101")
                        .cgpa(8.5)
                        .backlogs(0)
                        .branch("CSE")
                        .attendancePct(90.0)
                        .skills(List.of("Java"))
                        .build();

        List<Double> latencies = new ArrayList<>();

        // Run 1,000 rule evaluations
        for (int i = 0; i < 1000; i++) {

            long start = System.nanoTime();

            engine.evaluate(
                    ruleSet,
                    student,
                    Map.of()
            );

            double elapsedMs =
                    (System.nanoTime() - start)
                            / 1_000_000.0;

            latencies.add(elapsedMs);
        }

        // Sort latencies to calculate p95
        latencies.sort(Double::compareTo);

        double p95 = latencies.get(950);

        System.out.println(
                "Measured p95 Decision Latency: "
                        + p95 + " ms"
        );

        // KPI: p95 must be <= 200 ms
        Assertions.assertTrue(
                p95 <= 200.0,
                "Latency KPI Failed! p95 exceeds 200 ms"
        );
    }
}