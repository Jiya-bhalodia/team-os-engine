package com.placement.teama.algorithm.rule;

import com.placement.teama.model.entity.EligibilityRule;
import com.placement.teama.model.entity.StudentSnapshot;
import java.util.List;

public class RuleHelper {
    public static boolean checkRule(EligibilityRule rule, StudentSnapshot student, List<String> failedMessages) {
        String type = rule.getRuleType();
        Object thresh = rule.getThreshold();

        if ("min_cgpa".equalsIgnoreCase(type)) {
            double req = Double.parseDouble(thresh.toString());
            if (student.getCgpa() < req) {
                failedMessages.add("CGPA " + student.getCgpa() + " is below required cutoff of " + req);
                return false;
            }
        } else if ("max_backlogs".equalsIgnoreCase(type)) {
            int maxAllowed = Integer.parseInt(thresh.toString());
            if (student.getBacklogs() > maxAllowed) {
                failedMessages.add("Backlogs " + student.getBacklogs() + " exceeds allowed limit of " + maxAllowed);
                return false;
            }
        } else if ("allowed_branches".equalsIgnoreCase(type)) {
            @SuppressWarnings("unchecked")
            List<String> allowed = (List<String>) thresh;
            if (!allowed.contains(student.getBranch())) {
                failedMessages.add("Branch " + student.getBranch() + " not in eligible list " + allowed);
                return false;
            }
        } else if ("min_attendance".equalsIgnoreCase(type)) {
            double req = Double.parseDouble(thresh.toString());
            if (student.getAttendancePct() < req) {
                failedMessages.add("Attendance " + student.getAttendancePct() + "% below required " + req + "%");
                return false;
            }
        } else if ("required_skills".equalsIgnoreCase(type)) {
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) thresh;
            List<String> studentSkills = student.getSkills() != null ? student.getSkills() : List.of();
            List<String> missing = required.stream().filter(s -> !studentSkills.contains(s)).toList();
            if (!missing.isEmpty()) {
                failedMessages.add("Missing required skills: " + missing);
                return false;
            }
        }
        return true;
    }
}