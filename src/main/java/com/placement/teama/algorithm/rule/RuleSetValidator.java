package com.placement.teama.algorithm.rule;

import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.RuleSet;
import com.placement.teama.model.entity.StudentSnapshot;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates rule definitions and the student fields required to evaluate them. */
public final class RuleSetValidator {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "min_cgpa", "max_backlogs", "allowed_branches", "min_attendance", "required_skills");

    private RuleSetValidator() { }

    public static void validate(RuleSet ruleSet, StudentSnapshot student) {
        if (ruleSet == null) throw invalid("rule_set is required");
        if (ruleSet.getVersion() == null || ruleSet.getVersion().isBlank()) {
            throw invalid("rule_set.version is required");
        }
        if (ruleSet.getRules() == null || ruleSet.getRules().isEmpty()) {
            throw invalid("rule_set.rules must contain at least one rule");
        }
        if (student == null) throw invalid("student is required");
        if (student.getStudentId() == null || student.getStudentId().isBlank()) {
            throw invalid("student.student_id is required");
        }

        for (int i = 0; i < ruleSet.getRules().size(); i++) {
            EligibilityRule rule = ruleSet.getRules().get(i);
            String prefix = "rule_set.rules[" + i + "]";
            if (rule == null) throw invalid(prefix + " must be an object");
            String type = canonicalType(rule.getRuleType());
            if (type == null) throw invalid(prefix + ".rule_type is required");
            if (!SUPPORTED_TYPES.contains(type)) {
                throw invalid(prefix + ".rule_type is unsupported: " + rule.getRuleType());
            }
            validateWeight(rule, prefix);
            switch (type) {
                case "min_cgpa" -> {
                    double threshold = numericThreshold(rule.getThreshold(), prefix + ".threshold");
                    if (threshold < 0 || threshold > 10) throw invalid(prefix + ".threshold for min_cgpa must be between 0 and 10");
                    if (student.getCgpa() == null || !Double.isFinite(student.getCgpa())
                            || student.getCgpa() < 0 || student.getCgpa() > 10) {
                        throw invalid("student.cgpa is required and must be between 0 and 10 for min_cgpa");
                    }
                }
                case "max_backlogs" -> {
                    double threshold = numericThreshold(rule.getThreshold(), prefix + ".threshold");
                    if (threshold < 0 || threshold > Integer.MAX_VALUE || threshold != Math.rint(threshold)) {
                        throw invalid(prefix + ".threshold for max_backlogs must be a non-negative integer");
                    }
                    if (student.getBacklogs() == null || student.getBacklogs() < 0) {
                        throw invalid("student.backlogs is required and must be non-negative for max_backlogs");
                    }
                }
                case "min_attendance" -> {
                    double threshold = numericThreshold(rule.getThreshold(), prefix + ".threshold");
                    if (threshold < 0 || threshold > 100) throw invalid(prefix + ".threshold for min_attendance must be between 0 and 100");
                    if (student.getAttendancePct() == null || !Double.isFinite(student.getAttendancePct())
                            || student.getAttendancePct() < 0 || student.getAttendancePct() > 100) {
                        throw invalid("student.attendance_pct is required and must be between 0 and 100 for min_attendance");
                    }
                }
                case "allowed_branches" -> {
                    Object source = rule.getAllowedValues() != null ? rule.getAllowedValues() : rule.getThreshold();
                    validateStringList(source, prefix + ".allowed_values", true);
                    if (student.getBranch() == null || student.getBranch().isBlank()) {
                        throw invalid("student.branch is required for allowed_branches");
                    }
                }
                case "required_skills" -> validateStringList(rule.getThreshold(), prefix + ".threshold", false);
                default -> throw invalid(prefix + ".rule_type is unsupported: " + rule.getRuleType());
            }
        }
    }

    public static String canonicalType(String type) {
        return type == null || type.isBlank() ? null : type.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isHardRequirement(String type) {
        String canonical = canonicalType(type);
        return "min_cgpa".equals(canonical) || "max_backlogs".equals(canonical);
    }

    private static void validateWeight(EligibilityRule rule, String prefix) {
        if (!Double.isFinite(rule.getWeight()) || rule.getWeight() <= 0) {
            throw invalid(prefix + ".weight must be a finite number greater than zero");
        }
    }

    private static double numericThreshold(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw invalid(field + " must be a JSON number");
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result)) throw invalid(field + " must be finite");
        return result;
    }

    private static void validateStringList(Object value, String field, boolean nonEmpty) {
        if (!(value instanceof List<?> values)) throw invalid(field + " must be an array of strings");
        if (nonEmpty && values.isEmpty()) throw invalid(field + " must contain at least one value");
        for (Object item : values) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw invalid(field + " must contain only non-empty strings");
            }
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
