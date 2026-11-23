package com.schoolmanagement.assignment.repository;

import com.schoolmanagement.assignment.entity.AssignmentAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, UUID> {

    List<AssignmentAttachment> findAllByAssignmentIdOrderByCreatedAtAsc(UUID assignmentId);

    List<AssignmentAttachment> findAllByAssignmentIdIn(Collection<UUID> assignmentIds);

    Optional<AssignmentAttachment> findByIdAndAssignmentId(UUID id, UUID assignmentId);

    long countByAssignmentId(UUID assignmentId);
}
