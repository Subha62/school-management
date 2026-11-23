package com.schoolmanagement.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.exam.dto.ExamDtos.SubjectStatus;
import com.schoolmanagement.exam.entity.GradeRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResultCalculatorTest {

    private static final UUID SCHOOL = UUID.randomUUID();
    private static final List<GradeRule> SCALE = List.of(
            rule("F", "0", "39.99"), rule("C", "40", "59.99"), rule("B", "60", "79.99"), rule("A", "80", "100"));

    private static GradeRule rule(String grade, String min, String max) {
        return new GradeRule(SCHOOL, grade, null, new BigDecimal(min), new BigDecimal(max));
    }

    private static ResultCalculator.Line line(String name, String max, String pass, String marks) {
        return new ResultCalculator.Line(UUID.randomUUID(), name.substring(0, 3).toUpperCase(), name,
                new BigDecimal(max), new BigDecimal(pass), marks == null ? null : new BigDecimal(marks),
                marks == null, null);
    }

    @Test
    void totalsPercentageGradeAndPassForAStudentWhoPassesEverything() {
        var outcome = ResultCalculator.calculate(List.of(line("Mathematics", "100", "35", "90"),
                line("English", "50", "20", "40")), SCALE);

        assertThat(outcome.totalMarks()).isEqualByComparingTo("130");
        assertThat(outcome.totalMax()).isEqualByComparingTo("150");
        assertThat(outcome.percentage()).isEqualByComparingTo("86.67");
        assertThat(outcome.grade()).isEqualTo("A");
        assertThat(outcome.result()).isEqualTo("PASS");
        assertThat(outcome.subjects()).extracting(s -> s.status()).containsOnly(SubjectStatus.PASS);
        assertThat(outcome.subjects().get(1).percentage()).isEqualByComparingTo("80.00");
        assertThat(outcome.subjects().get(1).grade()).isEqualTo("A");
    }

    @Test
    void anAbsentSubjectScoresZeroKeepsItsMaximumInTheTotalAndFailsTheStudent() {
        var outcome = ResultCalculator.calculate(List.of(line("Mathematics", "100", "35", null),
                line("English", "100", "35", "100")), SCALE);

        assertThat(outcome.totalMarks()).isEqualByComparingTo("100");
        assertThat(outcome.totalMax()).isEqualByComparingTo("200");
        assertThat(outcome.percentage()).isEqualByComparingTo("50.00");
        assertThat(outcome.grade()).isEqualTo("C");
        assertThat(outcome.result()).isEqualTo("FAIL");
        var math = outcome.subjects().get(0);
        assertThat(math.status()).isEqualTo(SubjectStatus.ABSENT);
        assertThat(math.marks()).isNull();
        assertThat(math.grade()).isNull();
    }

    @Test
    void failingOneSubjectFailsTheStudentEvenWithAHighTotal() {
        var outcome = ResultCalculator.calculate(List.of(line("Mathematics", "100", "35", "100"),
                line("Science", "100", "35", "34.99")), SCALE);

        assertThat(outcome.percentage()).isEqualByComparingTo("67.50");
        assertThat(outcome.grade()).isEqualTo("B");
        assertThat(outcome.result()).isEqualTo("FAIL");
        assertThat(outcome.subjects().get(1).status()).isEqualTo(SubjectStatus.FAIL);
    }

    @Test
    void reachingThePassMarksExactlyIsAPass() {
        var outcome = ResultCalculator.calculate(List.of(line("Mathematics", "100", "35", "35")), SCALE);
        assertThat(outcome.subjects().get(0).status()).isEqualTo(SubjectStatus.PASS);
    }

    @ParameterizedTest
    @CsvSource({"0,F", "39.99,F", "40,C", "59.99,C", "60,B", "79.99,B", "80,A", "100,A"})
    void bandEdgesBelongToTheBandThatContainsThem(String percentage, String grade) {
        assertThat(ResultCalculator.gradeFor(new BigDecimal(percentage), SCALE)).isEqualTo(grade);
    }

    @Test
    void percentagesRoundHalfUpToTwoDecimals() {
        assertThat(ResultCalculator.percent(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("33.33");
        assertThat(ResultCalculator.percent(new BigDecimal("2"), new BigDecimal("3"))).isEqualByComparingTo("66.67");
        assertThat(ResultCalculator.percent(new BigDecimal("1"), new BigDecimal("8"))).isEqualByComparingTo("12.50");
        assertThat(ResultCalculator.percent(new BigDecimal("1.005"), new BigDecimal("100"))).isEqualByComparingTo("1.01");
    }

    @Test
    void aPercentageWithoutABandIsRefusedInsteadOfGuessingAGrade() {
        List<GradeRule> gappy = List.of(rule("A", "80", "100"));
        assertThatThrownBy(() -> ResultCalculator.gradeFor(new BigDecimal("50"), gappy))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no grade for 50%");
    }
}
