package com.schoolmanagement.exam.repository;

import com.schoolmanagement.exam.entity.Exam;
import jakarta.persistence.LockModeType;
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

public interface ExamRepository extends JpaRepository<Exam, UUID>, JpaSpecificationExecutor<Exam> {

    @EntityGraph(attributePaths = {"academicYear", "gradeLevel"})
    Optional<Exam> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"academicYear", "gradeLevel"})
    Page<Exam> findAll(Specification<Exam> spec, Pageable pageable);

    boolean existsByGradeLevelId(UUID gradeLevelId);

    boolean existsBySchoolIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(UUID schoolId, UUID academicYearId,
            UUID gradeLevelId, String name);

    boolean existsBySchoolIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot(UUID schoolId,
            UUID academicYearId, UUID gradeLevelId, String name, UUID id);

    /** Held while an administrator moves an exam between states, so marks cannot be saved halfway through. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exam e where e.id = :id and e.schoolId = :schoolId")
    Optional<Exam> lockForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);

    /** Held (shared) while marks are saved: a lock or publish must wait for them, and they cannot slip in after. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from Exam e where e.id = :id and e.schoolId = :schoolId")
    Optional<Exam> lockForMarks(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}
