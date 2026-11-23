package com.schoolmanagement.attendance.dto;

import com.schoolmanagement.attendance.entity.AttendanceCorrection;
import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.AttendanceStatus;
import com.schoolmanagement.attendance.entity.SessionStatus;
import com.schoolmanagement.attendance.entity.SessionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {

    public static final int MAX_RECORDS = 500;

    private AttendanceDtos() {
    }

    /** No subject means a daily session; a subject (and optional period) means a subject session. */
    public record OpenRequest(
            @NotNull(message = "Section is required") UUID sectionId,
            @NotNull(message = "Date is required") LocalDate date,
            UUID subjectId,
            @Min(value = 1, message = "Period must be at least 1") @Max(value = 20, message = "Period must be at most 20") Integer periodNumber) {
    }

    public record MarkItem(
            @NotNull(message = "Student is required") UUID studentId,
            @NotNull(message = "Status is required") AttendanceStatus status,
            @Size(max = 200, message = "Remarks must be at most 200 characters") String remarks) {
    }

    public record MarkRequest(
            @NotEmpty(message = "Mark at least one student") @Size(max = MAX_RECORDS, message = "At most 500 students at once") List<@Valid MarkItem> records) {
    }

    public record CorrectionRequest(
            @NotNull(message = "Student is required") UUID studentId,
            @NotNull(message = "Status is required") AttendanceStatus status,
            @Size(max = 200, message = "Remarks must be at most 200 characters") String remarks,
            @NotBlank(message = "A reason is required") @Size(min = 5, max = 500, message = "The reason must be 5 to 500 characters") String reason) {
    }

    public record Counts(int present, int absent, int late, int halfDay, int excused) {
        public static final Counts NONE = new Counts(0, 0, 0, 0, 0);

        public int total() {
            return present + absent + late + halfDay + excused;
        }
    }

    public record Entry(UUID studentId, String admissionNumber, String name, Integer rollNumber,
            AttendanceStatus status, String remarks) {
    }

    /** {@code enrolled} is the number of students currently in the section. */
    public record Summary(UUID id, LocalDate date, SessionType type, UUID sectionId, String sectionName,
            UUID subjectId, String subjectName, Integer periodNumber, SessionStatus status, Counts counts,
            int enrolled, Instant finalizedAt, long version) {

        public static Summary from(AttendanceSession s, Counts counts, int enrolled) {
            return new Summary(s.getId(), s.getAttendanceDate(), s.getType(), s.getSection().getId(),
                    s.getSection().getGradeLevel().getName() + " - " + s.getSection().getName(),
                    s.getSubject() == null ? null : s.getSubject().getId(),
                    s.getSubject() == null ? null : s.getSubject().getName(), s.getPeriodNumber(), s.getStatus(),
                    counts, enrolled, s.getFinalizedAt(), s.getVersion());
        }
    }

    public record Detail(Summary session, List<Entry> entries) {
    }

    public record CorrectionResponse(UUID id, UUID studentId, String studentName, String admissionNumber,
            AttendanceStatus previousStatus, AttendanceStatus newStatus, String reason, String correctedBy,
            Instant correctedAt) {

        public static CorrectionResponse from(AttendanceCorrection c, String correctedBy) {
            var user = c.getStudent().getUser();
            return new CorrectionResponse(c.getId(), c.getStudent().getId(),
                    (user.getFirstName() + " " + user.getLastName()).trim(), c.getStudent().getAdmissionNumber(),
                    c.getPreviousStatus(), c.getNewStatus(), c.getReason(), correctedBy, c.getCreatedAt());
        }
    }

    /** A class a teacher may take attendance for: daily if they are the class teacher, plus their subjects. */
    public record ClassOption(UUID sectionId, String sectionName, String academicYear, boolean daily,
            List<SubjectOption> subjects) {
    }

    public record SubjectOption(UUID id, String name) {
    }

    public record ReportRow(UUID studentId, String admissionNumber, String name, String sectionName, int present,
            int late, int halfDay, int absent, int excused, int counted, BigDecimal percentage,
            boolean belowThreshold) {
    }

    /** {@code truncated} is true when more rows matched than are returned. */
    public record Report(LocalDate from, LocalDate to, SessionType type, int threshold, boolean truncated,
            List<ReportRow> rows) {
    }

    public record StudentDay(LocalDate date, SessionType type, String subjectName, Integer periodNumber,
            AttendanceStatus status, String remarks) {
    }

    public record Rate(int present, int late, int halfDay, int absent, int excused, BigDecimal percentage) {
    }

    /** A student's own attendance for a month: daily and subject-wise rates, and every finalized mark. */
    public record StudentMonth(String month, int threshold, Rate daily, Rate subjectWise, List<StudentDay> days) {
    }
}
