package com.schoolmanagement.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.schoolmanagement.attendance.entity.AttendanceStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AttendanceTallyTest {

    private static AttendanceTally of(AttendanceStatus... statuses) {
        AttendanceTally tally = AttendanceTally.EMPTY;
        for (AttendanceStatus status : statuses) {
            tally = tally.plus(status);
        }
        return tally;
    }

    @Test
    void presentAndLateCountAsAttendedAndAbsentDoesNot() {
        AttendanceTally tally = of(AttendanceStatus.PRESENT, AttendanceStatus.LATE, AttendanceStatus.ABSENT,
                AttendanceStatus.PRESENT);

        assertThat(tally.counted()).isEqualTo(4);
        assertThat(tally.percentage()).isEqualByComparingTo("75.0");
    }

    @Test
    void aHalfDayCountsAsHalf() {
        assertThat(of(AttendanceStatus.HALF_DAY, AttendanceStatus.PRESENT).percentage())
                .isEqualByComparingTo("75.0");
        assertThat(of(AttendanceStatus.HALF_DAY).percentage()).isEqualByComparingTo("50.0");
    }

    @Test
    void excusedSessionsAreLeftOutOfBothSides() {
        AttendanceTally tally = of(AttendanceStatus.PRESENT, AttendanceStatus.EXCUSED, AttendanceStatus.EXCUSED);

        assertThat(tally.marked()).isEqualTo(3);
        assertThat(tally.counted()).isEqualTo(1);
        assertThat(tally.percentage()).isEqualByComparingTo("100.0");
    }

    @Test
    void thereIsNoPercentageWhenNothingCounts() {
        assertThat(AttendanceTally.EMPTY.percentage()).isNull();
        assertThat(of(AttendanceStatus.EXCUSED).percentage()).isNull();
        assertThat(AttendanceTally.EMPTY.isBelow(75)).isFalse();
    }

    @Test
    void roundsToOneDecimalPlace() {
        // 2 of 3 = 66.666...
        BigDecimal percentage = of(AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.ABSENT)
                .percentage();

        assertThat(percentage).isEqualByComparingTo("66.7");
    }

    @Test
    void theThresholdItselfIsNotBelow() {
        AttendanceTally exactly75 = of(AttendanceStatus.PRESENT, AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT, AttendanceStatus.ABSENT);

        assertThat(exactly75.isBelow(75)).isFalse();
        assertThat(exactly75.isBelow(76)).isTrue();
    }
}
