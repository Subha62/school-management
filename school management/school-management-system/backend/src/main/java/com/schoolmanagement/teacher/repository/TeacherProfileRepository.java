package com.schoolmanagement.teacher.repository;

import com.schoolmanagement.teacher.entity.TeacherProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TeacherProfileRepository
        extends JpaRepository<TeacherProfile, UUID>, JpaSpecificationExecutor<TeacherProfile> {

    @EntityGraph(attributePaths = "user")
    Optional<TeacherProfile> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<TeacherProfile> findAll(Specification<TeacherProfile> spec, Pageable pageable);

    Optional<TeacherProfile> findByUserId(UUID userId);

    boolean existsBySchoolIdAndEmployeeIdIgnoreCase(UUID schoolId, String employeeId);
}
