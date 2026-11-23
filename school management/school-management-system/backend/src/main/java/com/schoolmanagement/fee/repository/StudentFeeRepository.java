package com.schoolmanagement.fee.repository;

import com.schoolmanagement.fee.entity.StudentFee;
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

public interface StudentFeeRepository extends JpaRepository<StudentFee, UUID>, JpaSpecificationExecutor<StudentFee> {

    @EntityGraph(attributePaths = {"student", "student.user", "academicYear"})
    Optional<StudentFee> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"student", "student.user", "academicYear"})
    Page<StudentFee> findAll(Specification<StudentFee> spec, Pageable pageable);

    boolean existsByStructureId(UUID structureId);

    long countByStructureId(UUID structureId);

    @Query("select f.student.id from StudentFee f where f.structure.id = :structureId")
    List<UUID> findBilledStudentIds(@Param("structureId") UUID structureId);

    @Query("select f.structure.id, count(f) from StudentFee f where f.structure.id in :ids group by f.structure.id")
    List<Object[]> countByStructures(@Param("ids") Collection<UUID> ids);

    /**
     * Held while a payment, reversal or waiver changes the fee, so two cashiers cannot both pass the balance check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from StudentFee f where f.id = :id and f.schoolId = :schoolId")
    Optional<StudentFee> lockForUpdate(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}
