package com.schoolmanagement.attendance.service;

import com.schoolmanagement.attendance.entity.AttendanceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Counts of finalized attendance for one student and the percentage they imply.
 *
 * <p>Present and late count as attended, a half day as half, absent as not attended. Excused sessions are left
 * out of both sides, so an excused absence does not lower the percentage. With nothing to count there is no
 * percentage ({@code null}), which is different from 0%.
 */
public record AttendanceTally(int present, int late, int halfDay, int absent, int excused) {

    public static final AttendanceTally EMPTY = new AttendanceTally(0, 0, 0, 0, 0);

    public AttendanceTally plus(AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> new AttendanceTally(present + 1, late, halfDay, absent, excused);
            case LATE -> new AttendanceTally(present, late + 1, halfDay, absent, excused);
            case HALF_DAY -> new AttendanceTally(present, late, halfDay + 1, absent, excused);
            case ABSENT -> new AttendanceTally(present, late, halfDay, absent + 1, excused);
            case EXCUSED -> new AttendanceTally(present, late, halfDay, absent, excused + 1);
        };
    }

    public int marked() {
        return present + late + halfDay + absent + excused;
    }

    /** Sessions that count towards the percentage. */
    public int counted() {
        return marked() - excused;
    }

    /** Percentage with one decimal place, or {@code null} when no session counts. */
    public BigDecimal percentage() {
        if (counted() == 0) {
            return null;
        }
        // Work in halves so a half day is exact.
        long attendedHalves = 2L * (present + late) + halfDay;
        long countedHalves = 2L * counted();
        return BigDecimal.valueOf(attendedHalves * 100).divide(BigDecimal.valueOf(countedHalves), 1,
                RoundingMode.HALF_UP);
    }

    public boolean isBelow(int thresholdPercent) {
        BigDecimal percentage = percentage();
        return percentage != null && percentage.compareTo(BigDecimal.valueOf(thresholdPercent)) < 0;
    }
}
