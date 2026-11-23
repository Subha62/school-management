package com.schoolmanagement.academic.repository;

import com.schoolmanagement.academic.entity.Subject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SubjectRepository extends JpaRepository<Subject, UUID>, JpaSpecificationExecutor<Subject> {

    Optional<Subject> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndCodeIgnoreCase(UUID schoolId, String code);

    boolean existsBySchoolIdAndCodeIgnoreCaseAndIdNot(UUID schoolId, String code, UUID id);
}
