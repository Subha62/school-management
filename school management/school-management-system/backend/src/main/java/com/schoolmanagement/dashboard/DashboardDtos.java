package com.schoolmanagement.dashboard;

import com.schoolmanagement.audit.AuditLogService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** One timetable period of today. */
    public record Slot(int periodNumber, LocalTime startTime, LocalTime endTime, String subjectName,
            String sectionName, String room) {
    }

    // ---- administrators ---------------------------------------------------------------------------------

    public record AttendanceToday(long registersFinalized, long registersOpen) {
    }

    /** {@code null} on the dashboard when there is no active academic year. */
    public record FeeSnapshot(BigDecimal billed, BigDecimal collected, BigDecimal outstanding, BigDecimal overdue,
            long overdueFees) {
    }

    public record ExamSnapshot(long openForMarks, long awaitingPublication, long published) {
    }

    public record Admin(String academicYearName, long activeStudents, long activeTeachers, long activeClasses,
            AttendanceToday attendanceToday, FeeSnapshot fees, ExamSnapshot exams, long liveAnnouncements,
            List<AuditLogService.Entry> recentActivity) {
    }

    // ---- teachers ---------------------------------------------------------------------------------------

    public record Teacher(List<Slot> todaySlots, long classesTaught, long awaitingReview, long marksMissing,
            List<String> registersToTake, long unreadAnnouncements) {
    }

    // ---- students ---------------------------------------------------------------------------------------

    public record Upcoming(UUID assignmentId, String title, String subjectName, Instant dueAt, boolean resubmission) {
    }

    public record LatestResult(UUID examId, String examName, BigDecimal percentage, String grade, String result) {
    }

    public record StudentFees(BigDecimal outstanding, long overdueFees, LocalDate nextDueDate) {
    }

    /** {@code attendancePercent} is null until a register has been finalized this month. */
    public record Student(String className, List<Slot> todaySlots, BigDecimal attendancePercent,
            List<Upcoming> upcomingWork, LatestResult latestResult, StudentFees fees, long unreadAnnouncements) {
    }
}
