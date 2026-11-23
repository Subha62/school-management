package com.schoolmanagement.fee.repository;

import com.schoolmanagement.fee.entity.FeeStructure;
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

public interface FeeStructureRepository
        extends JpaRepository<FeeStructure, UUID>, JpaSpecificationExecutor<FeeStructure> {

    @EntityGraph(attributePaths = {"academicYear", "gradeLevel", "category"})
    Optional<FeeStructure> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"academicYear", "gradeLevel", "category"})
    Page<FeeStructure> findAll(Specification<FeeStructure> spec, Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);

    boolean existsByGradeLevelId(UUID gradeLevelId);

    boolean existsByAcademicYearIdAndGradeLevelIdAndCategoryIdAndDueDate(UUID academicYearId, UUID gradeLevelId,
            UUID categoryId, LocalDate dueDate);

    /** Held while fees are generated from, or the terms of, a structure change. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FeeStructure s where s.id = :id and s.schoolId = :schoolId")
    Optional<FeeStructure> lockForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}
