package com.schoolmanagement.attendance.repository;

import com.schoolmanagement.attendance.entity.AttendanceSession;
import com.schoolmanagement.attendance.entity.SessionType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceSessionRepository
        extends JpaRepository<AttendanceSession, UUID>, JpaSpecificationExecutor<AttendanceSession> {

    boolean existsBySectionId(UUID sectionId);

    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Optional<AttendanceSession> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Page<AttendanceSession> findAll(Specification<AttendanceSession> spec, Pageable pageable);

    /**
     * Locks the session row until the transaction ends, so marking and finalizing cannot interleave. The
     * associations are not fetched here: a lock cannot be taken across the optional subject join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AttendanceSession s where s.id = :id and s.schoolId = :schoolId")
    Optional<AttendanceSession> lockByIdAndSchoolId(@Param("id") UUID id, @Param("schoolId") UUID schoolId);

    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Optional<AttendanceSession> findBySectionIdAndAttendanceDateAndType(UUID sectionId, LocalDate date,
            SessionType type);

    /** {@code period} is 0 when the session has no period (the same convention as the unique index). */
    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    @Query("select s from AttendanceSession s where s.section.id = :sectionId and s.attendanceDate = :date "
            + "and s.type = com.schoolmanagement.attendance.entity.SessionType.SUBJECT "
            + "and s.subject.id = :subjectId and coalesce(s.periodNumber, 0) = :period")
    Optional<AttendanceSession> findSubjectSession(@Param("sectionId") UUID sectionId,
            @Param("date") LocalDate date, @Param("subjectId") UUID subjectId, @Param("period") int period);
}
