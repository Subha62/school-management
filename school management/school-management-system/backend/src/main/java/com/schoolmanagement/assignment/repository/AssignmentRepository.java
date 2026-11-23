package com.schoolmanagement.assignment.repository;

import com.schoolmanagement.assignment.entity.Assignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AssignmentRepository
        extends JpaRepository<Assignment, UUID>, JpaSpecificationExecutor<Assignment> {

    boolean existsBySectionId(UUID sectionId);

    boolean existsBySectionIdAndSubjectId(UUID sectionId, UUID subjectId);

    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Optional<Assignment> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Page<Assignment> findAll(Specification<Assignment> spec, Pageable pageable);
}
