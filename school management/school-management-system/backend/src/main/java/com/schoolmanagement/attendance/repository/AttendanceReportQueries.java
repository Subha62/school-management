package com.schoolmanagement.attendance.repository;

import com.schoolmanagement.attendance.entity.AttendanceStatus;
import com.schoolmanagement.attendance.entity.SessionType;
import com.schoolmanagement.attendance.service.AttendanceTally;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Aggregates finalized attendance per student, so reports are computed by the database, not row by row. */
@Repository
public class AttendanceReportQueries {

    /** One student's counts over a period. */
    public record StudentTally(UUID studentId, AttendanceTally tally) {
    }

    private static final String STATUS = "com.schoolmanagement.attendance.entity.AttendanceStatus.";

    private final EntityManager entityManager;

    public AttendanceReportQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<StudentTally> tallies(UUID schoolId, LocalDate from, LocalDate to, SessionType type,
            UUID sectionId, UUID subjectId) {
        StringBuilder jpql = new StringBuilder("select r.student.id");
        for (AttendanceStatus status : AttendanceStatus.values()) {
            jpql.append(", sum(case when r.status = ").append(STATUS).append(status.name())
                    .append(" then 1 else 0 end)");
        }
        jpql.append(" from AttendanceRecord r join r.session s where s.schoolId = :schoolId "
                + "and s.status = com.schoolmanagement.attendance.entity.SessionStatus.FINALIZED "
                + "and s.attendanceDate between :from and :to and s.type = :type");
        if (sectionId != null) {
            jpql.append(" and s.section.id = :sectionId");
        }
        if (subjectId != null) {
            jpql.append(" and s.subject.id = :subjectId");
        }
        jpql.append(" group by r.student.id");

        Query query = entityManager.createQuery(jpql.toString())
                .setParameter("schoolId", schoolId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("type", type);
        if (sectionId != null) {
            query.setParameter("sectionId", sectionId);
        }
        if (subjectId != null) {
            query.setParameter("subjectId", subjectId);
        }
        List<StudentTally> result = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            // Column order follows AttendanceStatus.values(): PRESENT, ABSENT, LATE, HALF_DAY, EXCUSED.
            result.add(new StudentTally((UUID) columns[0], new AttendanceTally(count(columns[1]),
                    count(columns[3]), count(columns[4]), count(columns[2]), count(columns[5]))));
        }
        return result;
    }

    private static int count(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
