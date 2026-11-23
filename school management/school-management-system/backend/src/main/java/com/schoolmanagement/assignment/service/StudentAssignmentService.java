package com.schoolmanagement.assignment.service;

import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.Assignment;
import com.schoolmanagement.assignment.entity.AssignmentStatus;
import com.schoolmanagement.assignment.entity.AssignmentSubmission;
import com.schoolmanagement.assignment.entity.SubmissionAttachment;
import com.schoolmanagement.assignment.repository.AssignmentAttachmentRepository;
import com.schoolmanagement.assignment.repository.AssignmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionAttachmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.file.UploadProperties;
import com.schoolmanagement.file.UploadService;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The student's side of assignments. A student can reach only the published or closed assignments of the class
 * they are enrolled in, and only their own submission. Anything else looks like a missing record.
 */
@Service
public class StudentAssignmentService {

    private static final String ENTITY = "AssignmentSubmission";
    private static final int MAX_ANSWER = 10_000;

    private final AssignmentRepository assignments;
    private final AssignmentAttachmentRepository assignmentFiles;
    private final SubmissionRepository submissions;
    private final SubmissionAttachmentRepository submissionFiles;
    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final AssignmentService teacherSide;
    private final UploadService uploads;
    private final UploadProperties limits;
    private final AssignmentMapper mapper;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public StudentAssignmentService(AssignmentRepository assignments,
            AssignmentAttachmentRepository assignmentFiles, SubmissionRepository submissions,
            SubmissionAttachmentRepository submissionFiles, StudentProfileRepository students,
            StudentEnrollmentRepository enrollments, AssignmentService teacherSide, UploadService uploads,
            UploadProperties limits, AssignmentMapper mapper, AuditEventPublisher audit, Clock clock) {
        this.assignments = assignments;
        this.assignmentFiles = assignmentFiles;
        this.submissions = submissions;
        this.submissionFiles = submissionFiles;
        this.students = students;
        this.enrollments = enrollments;
        this.teacherSide = teacherSide;
        this.uploads = uploads;
        this.limits = limits;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.StudentView> list(AuthenticatedUser actor, AssignmentStatus status,
            String search, Pageable pageable) {
        StudentProfile student = profile(actor);
        List<UUID> sectionIds = enrollments.findAllActiveByStudentId(student.getId()).stream()
                .map(e -> e.getSection().getId()).toList();
        if (sectionIds.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true, true);
        }
        AssignmentStatus visible = status == AssignmentStatus.PUBLISHED || status == AssignmentStatus.CLOSED
                ? status : null;
        Specification<Assignment> spec = Specification
                .<Assignment>where(SearchSpecs.equalTo("schoolId", actor.schoolId()))
                .and((root, query, cb) -> root.get("section").get("id").in(sectionIds))
                .and((root, query, cb) -> visible == null
                        ? root.get("status").in(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)
                        : cb.equal(root.get("status"), visible))
                .and(SearchSpecs.containsIgnoreCase("title", search));
        Page<Assignment> page = assignments.findAll(spec, pageable);
        List<UUID> ids = page.getContent().stream().map(Assignment::getId).toList();
        Map<UUID, AssignmentSubmission> mine = ids.isEmpty() ? Map.of()
                : submissions.findAllByStudentIdAndAssignmentIdIn(student.getId(), ids).stream()
                        .collect(Collectors.toMap(s -> s.getAssignment().getId(), Function.identity()));
        Map<UUID, List<AssignmentDtos.AttachmentResponse>> attachments = ids.isEmpty() ? Map.of()
                : assignmentFiles.findAllByAssignmentIdIn(ids).stream().collect(Collectors.groupingBy(
                        f -> f.getAssignment().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), AssignmentMapper::files)));
        return PageResponse.from(page, assignment -> view(assignment, mine.get(assignment.getId()),
                attachments.getOrDefault(assignment.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.StudentView get(AuthenticatedUser actor, UUID id) {
        StudentProfile student = profile(actor);
        Assignment assignment = visible(actor, student, id);
        AssignmentSubmission mine = submissions.findByAssignmentIdAndStudentId(id, student.getId()).orElse(null);
        return view(assignment, mine, mapper.assignmentFiles(id));
    }

    /**
     * Saves the student's answer: text, new files, or both. Sending the form again changes the answer until it is
     * reviewed. Files already attached are kept; remove them with {@link #removeAttachment}.
     */
    @Transactional
    public AssignmentDtos.StudentView submit(AuthenticatedUser actor, UUID id, String textAnswer,
            List<MultipartFile> uploaded) {
        StudentProfile student = profile(actor);
        Assignment assignment = visible(actor, student, id);
        AssignmentSubmission existing = submissions.findByAssignmentIdAndStudentId(id, student.getId()).orElse(null);
        boolean late = requireCanSubmit(assignment, existing);

        String text = textAnswer == null || textAnswer.isBlank() ? null : textAnswer.trim();
        if (text != null && text.length() > MAX_ANSWER) {
            throw new FieldValidationException("textAnswer", "The answer must be at most " + MAX_ANSWER
                    + " characters");
        }
        List<MultipartFile> incoming = uploaded == null ? List.of()
                : uploaded.stream().filter(file -> !file.isEmpty()).toList();
        long existingFiles = existing == null ? 0 : submissionFiles.countBySubmissionId(existing.getId());
        if (text == null && existingFiles + incoming.size() == 0) {
            throw new FieldValidationException("textAnswer", "Write an answer or attach a file");
        }
        if (existingFiles + incoming.size() > limits.maxSubmissionFiles()) {
            throw new BusinessRuleException("A submission can have at most " + limits.maxSubmissionFiles()
                    + " files.");
        }

        Instant now = clock.instant();
        AssignmentSubmission submission = existing;
        if (submission == null) {
            submission = new AssignmentSubmission(assignment, student, text, now, late);
        } else {
            submission.submit(text, now, late);
        }
        submission = submissions.saveAndFlush(submission);
        for (UploadService.StoredUpload stored : uploads.storeAll(incoming)) {
            submissionFiles.save(new SubmissionAttachment(submission, stored.originalName(), stored.key(),
                    stored.contentType(), stored.sizeBytes(), actor.id()));
        }
        submissionFiles.flush();
        audit.record(actor, "SUBMISSION_SUBMITTED", ENTITY, submission.getId(), (late ? "Late submission" : "Submission")
                + " for \"" + assignment.getTitle() + "\"");
        return view(assignment, submission, mapper.assignmentFiles(id));
    }

    @Transactional
    public AssignmentDtos.StudentView removeAttachment(AuthenticatedUser actor, UUID id, UUID attachmentId) {
        StudentProfile student = profile(actor);
        Assignment assignment = visible(actor, student, id);
        AssignmentSubmission submission = submissions.findByAssignmentIdAndStudentId(id, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Submission"));
        requireCanSubmit(assignment, submission);
        SubmissionAttachment attachment = submissionFiles.findByIdAndSubmissionId(attachmentId, submission.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File"));
        if (submission.getTextAnswer() == null && submissionFiles.countBySubmissionId(submission.getId()) <= 1) {
            throw new BusinessRuleException("A submission needs an answer or a file. Add one before removing this file.");
        }
        submissionFiles.delete(attachment);
        submissionFiles.flush();
        uploads.deleteAfterCommit(attachment.getStorageKey());
        audit.record(actor, "SUBMISSION_FILE_REMOVED", ENTITY, submission.getId(), "Removed a file from \""
                + assignment.getTitle() + "\"");
        return view(assignment, submission, mapper.assignmentFiles(id));
    }

    @Transactional(readOnly = true)
    public FileDownload downloadAssignmentFile(AuthenticatedUser actor, UUID id, UUID attachmentId) {
        visible(actor, profile(actor), id);
        var attachment = assignmentFiles.findByIdAndAssignmentId(attachmentId, id)
                .orElseThrow(() -> new ResourceNotFoundException("File"));
        return teacherSide.open(attachment.getStorageKey(), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public FileDownload downloadOwnFile(AuthenticatedUser actor, UUID id, UUID attachmentId) {
        StudentProfile student = profile(actor);
        visible(actor, student, id);
        AssignmentSubmission submission = submissions.findByAssignmentIdAndStudentId(id, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Submission"));
        SubmissionAttachment attachment = submissionFiles.findByIdAndSubmissionId(attachmentId, submission.getId())
                .orElseThrow(() -> new ResourceNotFoundException("File"));
        return teacherSide.open(attachment.getStorageKey(), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSizeBytes());
    }

    /** The student's own work across every assignment, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.SubmissionListItem> mine(AuthenticatedUser actor, Pageable pageable) {
        StudentProfile student = profile(actor);
        Specification<AssignmentSubmission> spec = (root, query, cb) -> cb.equal(root.get("student").get("id"),
                student.getId());
        Page<AssignmentSubmission> page = submissions.findAll(spec, pageable);
        return PageResponse.from(page, s -> mapper.listItem(s, true));
    }

    // ---- rules -----------------------------------------------------------------------------------------

    /**
     * Fails unless the student may change their answer now, and says whether it would be late. A resubmission the
     * teacher asked for is allowed even after the due date and is never late.
     */
    private boolean requireCanSubmit(Assignment assignment, AssignmentSubmission existing) {
        if (assignment.getAcademicYear().getStatus() != AcademicYearStatus.ACTIVE) {
            throw new BusinessRuleException("This academic year is no longer open for submissions.");
        }
        if (!assignment.isPublished()) {
            throw new BusinessRuleException("This assignment is closed for submissions.");
        }
        if (existing != null && !existing.isEditableByStudent()) {
            throw new BusinessRuleException("This work has been reviewed and can no longer be changed.");
        }
        if (existing != null && existing.isResubmissionRequested()) {
            return false;
        }
        boolean late = clock.instant().isAfter(assignment.getDueAt());
        if (late && !assignment.isAllowLate()) {
            throw new BusinessRuleException("The due date has passed and late submissions are not accepted.");
        }
        return late;
    }

    private AssignmentDtos.StudentView view(Assignment assignment, AssignmentSubmission submission,
            List<AssignmentDtos.AttachmentResponse> attachments) {
        boolean canSubmit = canSubmitNow(assignment, submission);
        return new AssignmentDtos.StudentView(assignment.getId(), assignment.getSubject().getName(),
                assignment.getSection().getGradeLevel().getName() + " - " + assignment.getSection().getName(),
                mapper.teacherName(assignment.getTeacherId()), assignment.getTitle(), assignment.getDescription(),
                assignment.getDueAt(), assignment.getMaxMarks(), assignment.isAllowLate(), assignment.getStatus(),
                attachments, submission == null ? null : mapper.submissionView(submission, true), canSubmit);
    }

    private boolean canSubmitNow(Assignment assignment, AssignmentSubmission submission) {
        if (!assignment.isPublished() || assignment.getAcademicYear().getStatus() != AcademicYearStatus.ACTIVE) {
            return false;
        }
        if (submission != null && !submission.isEditableByStudent()) {
            return false;
        }
        if (submission != null && submission.isResubmissionRequested()) {
            return true;
        }
        return assignment.isAllowLate() || !clock.instant().isAfter(assignment.getDueAt());
    }

    private StudentProfile profile(AuthenticatedUser actor) {
        return students.findByUserId(actor.id()).filter(p -> p.getSchoolId().equals(actor.schoolId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student"));
    }

    /** The assignment, if it is published or closed and belongs to the class the student is in. */
    private Assignment visible(AuthenticatedUser actor, StudentProfile student, UUID id) {
        Assignment assignment = assignments.findByIdAndSchoolId(id, actor.schoolId())
                .filter(Assignment::isVisibleToStudents)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment"));
        boolean inClass = enrollments.findAllActiveByStudentId(student.getId()).stream()
                .map(StudentEnrollment::getSection).anyMatch(s -> s.getId().equals(assignment.getSection().getId()));
        if (!inClass) {
            throw new ResourceNotFoundException("Assignment");
        }
        return assignment;
    }
}
