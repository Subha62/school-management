package com.schoolmanagement.fee.repository;

import com.schoolmanagement.fee.entity.FeeCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FeeCategoryRepository extends JpaRepository<FeeCategory, UUID>, JpaSpecificationExecutor<FeeCategory> {

    Optional<FeeCategory> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndNameIgnoreCase(UUID schoolId, String name);

    boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(UUID schoolId, String name, UUID id);
}
