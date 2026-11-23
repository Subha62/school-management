package com.schoolmanagement.academic.repository;

import com.schoolmanagement.academic.entity.GradeLevel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID>, JpaSpecificationExecutor<GradeLevel> {

    Optional<GradeLevel> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndNameIgnoreCase(UUID schoolId, String name);

    boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(UUID schoolId, String name, UUID id);
}
