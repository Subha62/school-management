package com.schoolmanagement.attendance.service;

import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.entity.AttendanceCorrection;
import com.schoolmanagement.attendance.entity.AttendanceRecord;
import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.AttendanceStatus;
import com.schoolmanagement.attendance.entity.SessionStatus;
import com.schoolmanagement.attendance.entity.SessionType;
import com.schoolmanagement.attendance.repository.AttendanceCorrectionRepository;
import com.schoolmanagement.attendance.repository.AttendanceRecordRepository;
import com.schoolmanagement.attendance.repository.AttendanceSessionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator tools for attendance: browsing every session and correcting marks with a recorded reason. */
@Service
public class AttendanceAdminService {

    private static final String ENTITY = "AttendanceSession";

    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final AttendanceCorrectionRepository corrections;
    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final AttendanceViewBuilder views;
    private final TeacherDirectory directory;
    private final AuditEventPublisher audit;

    public AttendanceAdminService(AttendanceSessionRepository sessions, AttendanceRecordRepository records,
            AttendanceCorrectionRepository corrections, StudentProfileRepository students,
            StudentEnrollmentRepository enrollments, AttendanceViewBuilder views, TeacherDirectory directory,
            AuditEventPublisher audit) {
        this.sessions = sessions;
        this.records = records;
        this.corrections = corrections;
        this.students = students;
        this.enrollments = enrollments;
        this.views = views;
        this.directory = directory;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDtos.Summary> search(UUID schoolId, UUID sectionId, LocalDate from, LocalDate to,
            SessionType type, SessionStatus status, Pageable pageable) {
        Specification<AttendanceSession> spec = Specification
                .<AttendanceSession>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("type", type))
                .and(SearchSpecs.equalTo("status", status));
        if (sectionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("section").get("id"), sectionId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("attendanceDate"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("attendanceDate"), to));
        }
        var page = sessions.findAll(spec, pageable);
        return new PageResponse<>(views.summaries(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.Detail detail(UUID schoolId, UUID id) {
        return views.detail(find(schoolId, id));
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.CorrectionResponse> corrections(UUID schoolId, UUID id) {
        find(schoolId, id);
        List<AttendanceCorrection> found = corrections.findAllBySessionIdOrderByCreatedAtAsc(id);
        var names = directory.displayNames(found.stream().map(AttendanceCorrection::getCorrectedBy)
                .collect(Collectors.toSet()));
        return found.stream()
                .map(c -> AttendanceDtos.CorrectionResponse.from(c, names.getOrDefault(c.getCorrectedBy(), "Unknown")))
                .toList();
    }

    /**
     * Changes (or adds) one student's mark. Works on open and finalized sessions alike, always needs a reason, and
     * leaves a permanent history row besides the audit event.
     */
    @Transactional
    public AttendanceDtos.Detail correct(AuthenticatedUser admin, UUID id, AttendanceDtos.CorrectionRequest request) {
        AttendanceSession session = sessions.lockByIdAndSchoolId(id, admin.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session"));
        StudentProfile student = students.findByIdAndSchoolId(request.studentId(), admin.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Student"));
        String remarks = request.remarks() == null || request.remarks().isBlank() ? null : request.remarks().trim();
        String reason = request.reason().trim();

        AttendanceRecord record = records.findBySessionIdAndStudentId(id, student.getId()).orElse(null);
        AttendanceStatus previous = record == null ? null : record.getStatus();
        if (record == null) {
            boolean enrolled = enrollments.findActiveBySectionId(session.getSection().getId()).stream()
                    .map(StudentEnrollment::getStudent).map(StudentProfile::getId)
                    .anyMatch(student.getId()::equals);
            if (!enrolled) {
                throw new BusinessRuleException("The student is not enrolled in this section.");
            }
            record = new AttendanceRecord(session, student, request.status(), remarks, admin.id());
        } else if (previous == request.status() && Objects.equals(record.getRemarks(), remarks)) {
            throw new BusinessRuleException("Nothing would change. Choose a different status or remarks.");
        } else {
            record.change(request.status(), remarks, admin.id());
        }
        records.saveAndFlush(record);
        corrections.saveAndFlush(
                new AttendanceCorrection(session, student, previous, request.status(), reason, admin.id()));
        audit.record(admin, "ATTENDANCE_CORRECTED", ENTITY, id, "Corrected " + student.getAdmissionNumber() + " in "
                + AttendanceService.describe(session) + " from " + (previous == null ? "unmarked" : previous)
                + " to " + request.status() + ": " + reason);
        return views.detail(session);
    }

    private AttendanceSession find(UUID schoolId, UUID id) {
        return sessions.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance session"));
    }
}
