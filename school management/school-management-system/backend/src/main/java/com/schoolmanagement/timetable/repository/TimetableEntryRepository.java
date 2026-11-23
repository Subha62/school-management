package com.schoolmanagement.timetable.repository;

import com.schoolmanagement.timetable.entity.TimetableEntry;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimetableEntryRepository
        extends JpaRepository<TimetableEntry, UUID>, JpaSpecificationExecutor<TimetableEntry> {

    @Query("select count(e) > 0 from TimetableEntry e where e.section.id = :sectionId")
    boolean existsBySectionId(@Param("sectionId") UUID sectionId);

    @Query("select count(e) > 0 from TimetableEntry e where e.section.id = :sectionId and e.subject.id = :subjectId")
    boolean existsBySectionIdAndSubjectId(@Param("sectionId") UUID sectionId, @Param("subjectId") UUID subjectId);

    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Optional<TimetableEntry> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Page<TimetableEntry> findAll(Specification<TimetableEntry> spec, Pageable pageable);

    /**
     * Active entries that share a weekday and a stretch of time and dates with the candidate and also share its
     * section, its teacher or its room. {@code roomKey} is the lower-case room, or an empty string for no room.
     */
    @EntityGraph(attributePaths = {"section", "section.gradeLevel", "subject"})
    @Query("select e from TimetableEntry e where e.schoolId = :schoolId "
            + "and e.status = com.schoolmanagement.timetable.entity.TimetableStatus.ACTIVE "
            + "and e.dayOfWeek = :day and e.startTime < :end and e.endTime > :start "
            + "and e.effectiveFrom <= :to and e.effectiveTo >= :from and e.id <> :excludeId "
            + "and (e.section.id = :sectionId or e.teacherId = :teacherId or lower(e.room) = :roomKey) "
            + "order by e.startTime")
    List<TimetableEntry> findConflicts(@Param("schoolId") UUID schoolId, @Param("day") DayOfWeek day,
            @Param("start") LocalTime start, @Param("end") LocalTime end, @Param("from") LocalDate from,
            @Param("to") LocalDate to, @Param("excludeId") UUID excludeId, @Param("sectionId") UUID sectionId,
            @Param("teacherId") UUID teacherId, @Param("roomKey") String roomKey);

    @EntityGraph(attributePaths = {"section", "section.gradeLevel", "subject"})
    @Query("select e from TimetableEntry e where e.section.id = :sectionId "
            + "and e.status = com.schoolmanagement.timetable.entity.TimetableStatus.ACTIVE "
            + "and e.effectiveFrom <= :weekEnd and e.effectiveTo >= :weekStart")
    List<TimetableEntry> findActiveForSectionInWeek(@Param("sectionId") UUID sectionId,
            @Param("weekStart") LocalDate weekStart, @Param("weekEnd") LocalDate weekEnd);

    @EntityGraph(attributePaths = {"section", "section.gradeLevel", "subject"})
    @Query("select e from TimetableEntry e where e.schoolId = :schoolId and e.teacherId = :teacherId "
            + "and e.status = com.schoolmanagement.timetable.entity.TimetableStatus.ACTIVE "
            + "and e.effectiveFrom <= :weekEnd and e.effectiveTo >= :weekStart")
    List<TimetableEntry> findActiveForTeacherInWeek(@Param("schoolId") UUID schoolId,
            @Param("teacherId") UUID teacherId, @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd);

    /** Serialises timetable writes of one school, so two requests cannot both pass the conflict check. */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:key))) as locked",
            nativeQuery = true)
    long lockWrites(@Param("key") String key);
}
