package com.schoolmanagement.assignment.repository;

import com.schoolmanagement.assignment.entity.AssignmentSubmission;
import com.schoolmanagement.assignment.entity.SubmissionStatus;
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubmissionRepository
        extends JpaRepository<AssignmentSubmission, UUID>, JpaSpecificationExecutor<AssignmentSubmission> {

    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<AssignmentSubmission> findByIdAndAssignmentId(UUID id, UUID assignmentId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<AssignmentSubmission> findAllByAssignmentId(UUID assignmentId);

    boolean existsByAssignmentId(UUID assignmentId);

    /** A student's own submissions to the given assignments, so a list needs one query. */
    List<AssignmentSubmission> findAllByStudentIdAndAssignmentIdIn(UUID studentId, Collection<UUID> assignmentIds);

    /** Rows of {@code [assignmentId, status, count]} for the given assignments. */
    @Query("select s.assignment.id, s.status, count(s) from AssignmentSubmission s "
            + "where s.assignment.id in :assignmentIds group by s.assignment.id, s.status")
    List<Object[]> countByStatus(@Param("assignmentIds") Collection<UUID> assignmentIds);

    @Override
    @EntityGraph(attributePaths = {"assignment", "assignment.section", "assignment.section.gradeLevel",
            "assignment.subject", "student", "student.user"})
    Page<AssignmentSubmission> findAll(Specification<AssignmentSubmission> spec, Pageable pageable);

    long countByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);
}
