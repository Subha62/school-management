package com.schoolmanagement.academic.repository;

import com.schoolmanagement.academic.entity.Section;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
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

public interface SectionRepository extends JpaRepository<Section, UUID>, JpaSpecificationExecutor<Section> {

    @EntityGraph(attributePaths = {"academicYear", "gradeLevel"})
    Optional<Section> findByIdAndSchoolId(UUID id, UUID schoolId);

    /** The graph loads year and grade in the same query, which avoids N+1 selects when mapping lists. */
    @Override
    @EntityGraph(attributePaths = {"academicYear", "gradeLevel"})
    Page<Section> findAll(Specification<Section> spec, Pageable pageable);

    boolean existsByAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(UUID academicYearId, UUID gradeLevelId,
            String name);

    boolean existsByAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot(UUID academicYearId,
            UUID gradeLevelId, String name, UUID id);

    boolean existsByAcademicYearId(UUID academicYearId);

    @EntityGraph(attributePaths = {"academicYear", "gradeLevel"})
    List<Section> findAllByClassTeacherIdAndSchoolId(UUID classTeacherId, UUID schoolId);

    boolean existsByGradeLevelId(UUID gradeLevelId);

    /**
     * Locks the given sections (in id order, so concurrent callers cannot deadlock) until the transaction ends.
     * Enrollment takes this lock before checking capacity, so two requests cannot both take the last seat.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Section s join fetch s.academicYear join fetch s.gradeLevel "
            + "where s.id in :ids and s.schoolId = :schoolId order by s.id")
    List<Section> lockAllByIdIn(@Param("ids") Collection<UUID> ids, @Param("schoolId") UUID schoolId);
}
