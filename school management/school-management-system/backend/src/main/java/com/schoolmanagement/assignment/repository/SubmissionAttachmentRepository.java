package com.schoolmanagement.assignment.repository;

import com.schoolmanagement.assignment.entity.SubmissionAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, UUID> {

    List<SubmissionAttachment> findAllBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    List<SubmissionAttachment> findAllBySubmissionIdIn(Collection<UUID> submissionIds);

    Optional<SubmissionAttachment> findByIdAndSubmissionId(UUID id, UUID submissionId);

    long countBySubmissionId(UUID submissionId);
}
