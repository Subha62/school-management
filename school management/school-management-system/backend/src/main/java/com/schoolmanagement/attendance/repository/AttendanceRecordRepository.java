package com.schoolmanagement.attendance.repository;

import com.schoolmanagement.attendance.entity.AttendanceRecord;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<AttendanceRecord> findAllBySessionId(UUID sessionId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    long countBySessionId(UUID sessionId);

    /** Rows of {@code [sessionId, status, count]} for the given sessions, so a list needs one query. */
    @Query("select r.session.id, r.status, count(r) from AttendanceRecord r where r.session.id in :sessionIds "
            + "group by r.session.id, r.status")
    List<Object[]> countByStatus(@Param("sessionIds") Collection<UUID> sessionIds);

    /** A student's finalized records for a date range, oldest first. */
    @Query("select r from AttendanceRecord r join fetch r.session s left join fetch s.subject "
            + "where r.student.id = :studentId and s.status = com.schoolmanagement.attendance.entity.SessionStatus.FINALIZED "
            + "and s.attendanceDate between :from and :to order by s.attendanceDate, s.periodNumber")
    List<AttendanceRecord> findFinalizedForStudent(@Param("studentId") UUID studentId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
