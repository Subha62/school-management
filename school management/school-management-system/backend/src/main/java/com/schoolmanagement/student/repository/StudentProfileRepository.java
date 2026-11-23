package com.schoolmanagement.student.repository;

import com.schoolmanagement.student.entity.StudentProfile;
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

public interface StudentProfileRepository
        extends JpaRepository<StudentProfile, UUID>, JpaSpecificationExecutor<StudentProfile> {

    @EntityGraph(attributePaths = "user")
    Optional<StudentProfile> findByIdAndSchoolId(UUID id, UUID schoolId);

    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findAllByIdInAndSchoolId(Collection<UUID> ids, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<StudentProfile> findAll(Specification<StudentProfile> spec, Pageable pageable);

    boolean existsBySchoolIdAndAdmissionNumberIgnoreCase(UUID schoolId, String admissionNumber);

    Optional<StudentProfile> findByUserId(UUID userId);
}
