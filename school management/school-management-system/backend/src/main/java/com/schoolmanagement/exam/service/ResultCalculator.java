package com.schoolmanagement.exam.service;

import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.exam.dto.ExamDtos.SubjectResult;
import com.schoolmanagement.exam.dto.ExamDtos.SubjectStatus;
import com.schoolmanagement.exam.entity.GradeRule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * The marks and grading rules in one place, free of persistence so they can be tested directly.
 *
 * <p>An absent student scores nothing in that subject but the subject's maximum still counts towards the total,
 * and the subject counts as failed. A subject is passed when the marks reach the pass marks (not the rounded
 * percentage). Percentages are rounded half up to two decimals, the precision of the grade scale.
 */
public final class ResultCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ResultCalculator() {
    }

    /** One subject of a student's exam as entered; {@code marks} is null exactly when the student was absent. */
    public record Line(UUID subjectId, String subjectCode, String subjectName, BigDecimal maxMarks,
            BigDecimal passMarks, BigDecimal marks, boolean absent, String remarks) {
    }

    public record Outcome(List<SubjectResult> subjects, BigDecimal totalMarks, BigDecimal totalMax,
            BigDecimal percentage, String grade, String result) {
    }

    public static Outcome calculate(List<Line> lines, List<GradeRule> scale) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A result needs at least one subject");
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        boolean allPassed = true;
        List<SubjectResult> subjects = new java.util.ArrayList<>();
        for (Line line : lines) {
            BigDecimal obtained = line.absent() ? BigDecimal.ZERO : line.marks();
            SubjectStatus status = line.absent() ? SubjectStatus.ABSENT
                    : obtained.compareTo(line.passMarks()) >= 0 ? SubjectStatus.PASS : SubjectStatus.FAIL;
            BigDecimal percentage = percent(obtained, line.maxMarks());
            subjects.add(new SubjectResult(line.subjectId(), line.subjectCode(), line.subjectName(),
                    line.maxMarks(), line.passMarks(), line.absent() ? null : obtained.setScale(2), line.absent(),
                    percentage, line.absent() ? null : gradeFor(percentage, scale), status, line.remarks()));
            total = total.add(obtained);
            totalMax = totalMax.add(line.maxMarks());
            allPassed &= status == SubjectStatus.PASS;
        }
        BigDecimal percentage = percent(total, totalMax);
        return new Outcome(subjects, total.setScale(2, RoundingMode.HALF_UP), totalMax.setScale(2), percentage,
                gradeFor(percentage, scale), allPassed ? "PASS" : "FAIL");
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    public static String gradeFor(BigDecimal percentage, List<GradeRule> scale) {
        return scale.stream().filter(rule -> rule.contains(percentage)).map(GradeRule::getGrade).findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "The grade scale has no grade for " + percentage.stripTrailingZeros().toPlainString() + "%."));
    }
}
