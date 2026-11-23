package com.schoolmanagement.assignment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "submission_attachments")
public class SubmissionAttachment extends FileAttachment {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false, updatable = false)
    private AssignmentSubmission submission;

    protected SubmissionAttachment() {
    }

    public SubmissionAttachment(AssignmentSubmission submission, String originalName, String storageKey,
            String contentType, long sizeBytes, UUID uploadedBy) {
        super(originalName, storageKey, contentType, sizeBytes, uploadedBy);
        this.submission = submission;
    }

    public AssignmentSubmission getSubmission() {
        return submission;
    }
}
