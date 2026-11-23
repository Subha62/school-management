package com.schoolmanagement.timetable.dto;

import com.schoolmanagement.timetable.entity.TimetableEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class TimetableDtos {

    private TimetableDtos() {
    }

    /**
     * {@code sectionId} is used when creating and ignored on update. A missing {@code teacherId} means the
     * teacher assigned to the subject in that section; missing dates mean the whole academic year.
     */
    public record Request(
            UUID sectionId,
            @NotNull(message = "Subject is required") UUID subjectId,
            UUID teacherId,
            @Size(max = 30, message = "Room must be at most 30 characters") String room,
            @NotNull(message = "Day is required") DayOfWeek dayOfWeek,
            @NotNull(message = "Period is required") @Min(value = 1, message = "Period must be at least 1") @Max(value = 20, message = "Period must be at most 20") Integer periodNumber,
            @NotNull(message = "Start time is required") LocalTime startTime,
            @NotNull(message = "End time is required") LocalTime endTime,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotNull(message = "Active flag is required") Boolean active,
            Long version) {
    }

    /** {@code date} is the calendar date of the slot in weekly views and null elsewhere. */
    public record Response(UUID id, UUID academicYearId, String academicYearName, UUID sectionId,
            String sectionName, UUID subjectId, String subjectCode, String subjectName, UUID teacherId,
            String teacherName, String room, DayOfWeek dayOfWeek, int periodNumber, LocalTime startTime,
            LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveTo, boolean active, LocalDate date,
            long version) {

        public static Response from(TimetableEntry e, String teacherName, LocalDate date) {
            return new Response(e.getId(), e.getAcademicYear().getId(), e.getAcademicYear().getName(),
                    e.getSection().getId(),
                    e.getSection().getGradeLevel().getName() + " - " + e.getSection().getName(),
                    e.getSubject().getId(), e.getSubject().getCode(), e.getSubject().getName(), e.getTeacherId(),
                    teacherName, e.getRoom(), e.getDayOfWeek(), e.getPeriodNumber(), e.getStartTime(),
                    e.getEndTime(), e.getEffectiveFrom(), e.getEffectiveTo(), e.isActive(), date, e.getVersion());
        }
    }

    public record Week(LocalDate weekStart, LocalDate weekEnd, List<Response> entries) {
    }
}
