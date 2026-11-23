package com.schoolmanagement.attendance.service;

import com.schoolmanagement.attendance.dto.AttendanceDtos;
import com.schoolmanagement.attendance.entity.AttendanceRecord;
import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.AttendanceStatus;
import com.schoolmanagement.attendance.repository.AttendanceRecordRepository;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Turns sessions into the list and detail views shared by the teacher and administrator APIs. */
@Component
public class AttendanceViewBuilder {

    private final AttendanceRecordRepository records;
    private final StudentEnrollmentRepository enrollments;

    public AttendanceViewBuilder(AttendanceRecordRepository records, StudentEnrollmentRepository enrollments) {
        this.records = records;
        this.enrollments = enrollments;
    }

    public List<AttendanceDtos.Summary> summaries(Collection<AttendanceSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<AttendanceStatus, Integer>> byStatus = new HashMap<>();
        for (Object[] row : records.countByStatus(sessions.stream().map(AttendanceSession::getId).toList())) {
            byStatus.computeIfAbsent((UUID) row[0], key -> new EnumMap<>(AttendanceStatus.class))
                    .put((AttendanceStatus) row[1], ((Number) row[2]).intValue());
        }
        Map<UUID, Integer> enrolled = new HashMap<>();
        return sessions.stream().map(session -> AttendanceDtos.Summary.from(session,
                counts(byStatus.get(session.getId())),
                enrolled.computeIfAbsent(session.getSection().getId(), this::enrolledIn))).toList();
    }

    public AttendanceDtos.Summary summary(AttendanceSession session) {
        return summaries(List.of(session)).get(0);
    }

    /**
     * The roster in roll-number order with each student's mark (null while unmarked), followed by marked students
     * who have since left the section, so a finalized session never loses a record.
     */
    public AttendanceDtos.Detail detail(AttendanceSession session) {
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(session.getSection().getId());
        Map<UUID, AttendanceRecord> marks = new HashMap<>();
        records.findAllBySessionId(session.getId()).forEach(record -> marks.put(record.getStudent().getId(), record));

        Map<UUID, AttendanceDtos.Entry> entries = new LinkedHashMap<>();
        for (StudentEnrollment enrollment : roster) {
            var student = enrollment.getStudent();
            AttendanceRecord mark = marks.get(student.getId());
            entries.put(student.getId(), entry(student.getId(), student.getAdmissionNumber(),
                    student.getUser().getFirstName(), student.getUser().getLastName(), enrollment.getRollNumber(),
                    mark));
        }
        marks.forEach((studentId, mark) -> entries.computeIfAbsent(studentId, key -> entry(studentId,
                mark.getStudent().getAdmissionNumber(), mark.getStudent().getUser().getFirstName(),
                mark.getStudent().getUser().getLastName(), null, mark)));
        return new AttendanceDtos.Detail(summary(session), new ArrayList<>(entries.values()));
    }

    private static AttendanceDtos.Entry entry(UUID studentId, String admissionNumber, String firstName,
            String lastName, Integer roll, AttendanceRecord mark) {
        return new AttendanceDtos.Entry(studentId, admissionNumber, (firstName + " " + lastName).trim(), roll,
                mark == null ? null : mark.getStatus(), mark == null ? null : mark.getRemarks());
    }

    private int enrolledIn(UUID sectionId) {
        return (int) enrollments.countBySectionIdAndStatus(sectionId, EnrollmentStatus.ACTIVE);
    }

    private static AttendanceDtos.Counts counts(Map<AttendanceStatus, Integer> byStatus) {
        if (byStatus == null) {
            return AttendanceDtos.Counts.NONE;
        }
        return new AttendanceDtos.Counts(byStatus.getOrDefault(AttendanceStatus.PRESENT, 0),
                byStatus.getOrDefault(AttendanceStatus.ABSENT, 0), byStatus.getOrDefault(AttendanceStatus.LATE, 0),
                byStatus.getOrDefault(AttendanceStatus.HALF_DAY, 0),
                byStatus.getOrDefault(AttendanceStatus.EXCUSED, 0));
    }
}
