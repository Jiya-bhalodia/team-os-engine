package com.placement.teama.algorithm.rule;

import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.StudentSnapshot;
import java.util.List;

public class RuleHelper {
    public static boolean checkRule(EligibilityRule rule, StudentSnapshot student, List<String> failedMessages) {
        if (rule == null) throw new IllegalArgumentException("rule must not be null");
        if (student == null) throw new IllegalArgumentException("student is required to evaluate a rule");
        String type = RuleSetValidator.canonicalType(rule.getRuleType());
        Object thresh = rule.getThreshold();

        if ("min_cgpa".equals(type)) {
            double req = ((Number) thresh).doubleValue();
            if (student.getCgpa() < req) {
                failedMessages.add("CGPA " + student.getCgpa() + " is below required cutoff of " + req);
                return false;
            }
        } else if ("max_backlogs".equals(type)) {
            int maxAllowed = ((Number) thresh).intValue();
            if (student.getBacklogs() > maxAllowed) {
                failedMessages.add("Backlogs " + student.getBacklogs() + " exceeds allowed limit of " + maxAllowed);
                return false;
            }
        } else if ("allowed_branches".equals(type)) {
            Object branchValues = rule.getAllowedValues() != null ? rule.getAllowedValues() : thresh;
            if (!(branchValues instanceof List<?>)) {
                failedMessages.add("Rule " + rule.getRuleId() + " must provide an allowed_values list");
                return false;
            }
            @SuppressWarnings("unchecked")
            List<String> allowed = (List<String>) branchValues;
            if (!allowed.contains(student.getBranch())) {
                failedMessages.add("Branch " + student.getBranch() + " not in eligible list " + allowed);
                return false;
            }
        } else if ("min_attendance".equals(type)) {
            double req = ((Number) thresh).doubleValue();
            if (student.getAttendancePct() < req) {
                failedMessages.add("Attendance " + student.getAttendancePct() + "% below required " + req + "%");
                return false;
            }
        } else if ("required_skills".equals(type)) {
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) thresh;
            List<String> studentSkills = student.getSkills() != null ? student.getSkills() : List.of();
            List<String> missing = required.stream().filter(s -> !studentSkills.contains(s)).toList();
            if (!missing.isEmpty()) {
                failedMessages.add("Missing required skills: " + missing);
                return false;
            }
        } else {
            throw new IllegalArgumentException(type == null
                    ? "rule_type is required"
                    : "Unsupported rule_type: " + rule.getRuleType());
        }
        return true;
    }
}
