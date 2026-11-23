package com.schoolmanagement.assignment.service;

import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.Assignment;
import com.schoolmanagement.assignment.entity.AssignmentAttachment;
import com.schoolmanagement.assignment.entity.AssignmentStatus;
import com.schoolmanagement.assignment.entity.AssignmentSubmission;
import com.schoolmanagement.assignment.entity.SubmissionAttachment;
import com.schoolmanagement.assignment.entity.SubmissionStatus;
import com.schoolmanagement.assignment.repository.AssignmentAttachmentRepository;
import com.schoolmanagement.assignment.repository.AssignmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionAttachmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.file.StoredFileMissingException;
import com.schoolmanagement.file.UploadProperties;
import com.schoolmanagement.file.UploadService;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The teacher's side of assignments: authoring and publishing, files, and reviewing what students hand in. A
 * teacher only ever reaches assignments they own; another teacher's id looks like a missing record.
 */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);
    private static final String ENTITY = "Assignment";

    private final AssignmentRepository assignments;
    private final AssignmentAttachmentRepository files;
    private final SubmissionRepository submissions;
    private final SubmissionAttachmentRepository submissionFiles;
    private final SectionRepository sections;
    private final SubjectRepository subjects;
    private final SectionSubjectRepository sectionSubjects;
    private final StudentEnrollmentRepository enrollments;
    private final UploadService uploads;
    private final UploadProperties uploadLimits;
    private final AssignmentMapper mapper;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignments, AssignmentAttachmentRepository files,
            SubmissionRepository submissions, SubmissionAttachmentRepository submissionFiles,
            SectionRepository sections, SubjectRepository subjects, SectionSubjectRepository sectionSubjects,
            StudentEnrollmentRepository enrollments, UploadService uploads, UploadProperties uploadLimits,
            AssignmentMapper mapper, AuditEventPublisher audit, Clock clock) {
        this.assignments = assignments;
        this.files = files;
        this.submissions = submissions;
        this.submissionFiles = submissionFiles;
        this.sections = sections;
        this.subjects = subjects;
        this.sectionSubjects = sectionSubjects;
        this.enrollments = enrollments;
        this.uploads = uploads;
        this.uploadLimits = uploadLimits;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- authoring -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.Response> list(AuthenticatedUser teacher, UUID sectionId,
            AssignmentStatus status, String search, Pageable pageable) {
        Specification<Assignment> spec = Specification
                .<Assignment>where(SearchSpecs.equalTo("schoolId", teacher.schoolId()))
                .and(SearchSpecs.equalTo("teacherId", teacher.id()))
                .and(SearchSpecs.equalTo("status", status))
                .and(SearchSpecs.containsIgnoreCase("title", search));
        if (status == null) {
            // Archived work is out of sight unless it is asked for.
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), AssignmentStatus.ARCHIVED));
        }
        if (sectionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("section").get("id"), sectionId));
        }
        Page<Assignment> page = assignments.findAll(spec, pageable);
        List<AssignmentDtos.Response> views = mapper.teacherViews(page.getContent());
        return new PageResponse<>(views, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.Response get(AuthenticatedUser teacher, UUID id) {
        return mapper.teacherView(owned(teacher, id));
    }

    @Transactional
    public AssignmentDtos.Response create(AuthenticatedUser teacher, AssignmentDtos.Request request) {
        if (request.sectionId() == null) {
            throw new FieldValidationException("sectionId", "Class is required");
        }
        if (request.subjectId() == null) {
            throw new FieldValidationException("subjectId", "Subject is required");
        }
        Section section = sections.findByIdAndSchoolId(request.sectionId(), teacher.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        if (!sectionSubjects.existsBySectionIdAndSubjectIdAndTeacherIdAndActiveTrue(section.getId(),
                request.subjectId(), teacher.id())) {
            throw new AccessDeniedException("Not the teacher of this subject in this class");
        }
        requireOpenYear(section);
        Subject subject = subjects.findByIdAndSchoolId(request.subjectId(), teacher.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject"));
        requireFuture(request.dueAt());

        Assignment assignment = assignments.saveAndFlush(new Assignment(section, subject, teacher.id(),
                request.title().trim(), blankToNull(request.description()), request.dueAt(), request.maxMarks(),
                request.allowLate()));
        audit.record(teacher, "ASSIGNMENT_CREATED", ENTITY, assignment.getId(), describe(assignment));
        return mapper.teacherView(assignment);
    }

    @Transactional
    public AssignmentDtos.Response update(AuthenticatedUser teacher, UUID id, AssignmentDtos.Request request) {
        Assignment assignment = owned(teacher, id);
        OptimisticVersion.check(request.version(), assignment.getVersion());
        if (assignment.getStatus() == AssignmentStatus.ARCHIVED) {
            throw new BusinessRuleException("An archived assignment cannot be changed.");
        }
        if (request.maxMarks().compareTo(assignment.getMaxMarks()) != 0
                && submissions.existsByAssignmentId(id)) {
            throw new BusinessRuleException("Maximum marks cannot change once students have submitted work.");
        }
        if (!request.dueAt().equals(assignment.getDueAt())) {
            requireFuture(request.dueAt());
        }
        assignment.update(request.title().trim(), blankToNull(request.description()), request.dueAt(),
                request.maxMarks(), request.allowLate());
        assignments.saveAndFlush(assignment);
        audit.record(teacher, "ASSIGNMENT_UPDATED", ENTITY, id, describe(assignment));
        return mapper.teacherView(assignment);
    }

    @Transactional
    public AssignmentDtos.Response publish(AuthenticatedUser teacher, UUID id) {
        Assignment assignment = owned(teacher, id);
        if (assignment.isDraft()) {
            requireOpenYear(assignment.getSection());
            if (!assignment.getSection().isActive()) {
                throw new BusinessRuleException("The class is inactive.");
            }
            requireFuture(assignment.getDueAt());
        }
        assignment.publish(clock.instant());
        return transitioned(teacher, assignment, "ASSIGNMENT_PUBLISHED");
    }

    @Transactional
    public AssignmentDtos.Response close(AuthenticatedUser teacher, UUID id) {
        Assignment assignment = owned(teacher, id);
        assignment.close(clock.instant());
        return transitioned(teacher, assignment, "ASSIGNMENT_CLOSED");
    }

    @Transactional
    public AssignmentDtos.Response reopen(AuthenticatedUser teacher, UUID id) {
        Assignment assignment = owned(teacher, id);
        requireOpenYear(assignment.getSection());
        if (!assignment.getDueAt().isAfter(clock.instant()) && !assignment.isAllowLate()) {
            throw new BusinessRuleException(
                    "Extend the due date or allow late submissions before reopening this assignment.");
        }
        assignment.reopen();
        return transitioned(teacher, assignment, "ASSIGNMENT_REOPENED");
    }

    @Transactional
    public AssignmentDtos.Response archive(AuthenticatedUser teacher, UUID id) {
        Assignment assignment = owned(teacher, id);
        assignment.archive();
        return transitioned(teacher, assignment, "ASSIGNMENT_ARCHIVED");
    }

    /** Only a draft can be deleted: once students could see it, it is closed and archived instead. */
    @Transactional
    public void delete(AuthenticatedUser teacher, UUID id) {
        Assignment assignment = owned(teacher, id);
        if (!assignment.isDraft()) {
            throw new BusinessRuleException("Only a draft can be deleted. Close and archive a published assignment.");
        }
        List<AssignmentAttachment> attached = files.findAllByAssignmentIdOrderByCreatedAtAsc(id);
        files.deleteAll(attached);
        assignments.delete(assignment);
        attached.forEach(file -> uploads.deleteAfterCommit(file.getStorageKey()));
        audit.record(teacher, "ASSIGNMENT_DELETED", ENTITY, id, describe(assignment));
    }

    private AssignmentDtos.Response transitioned(AuthenticatedUser teacher, Assignment assignment, String action) {
        assignments.saveAndFlush(assignment);
        audit.record(teacher, action, ENTITY, assignment.getId(), describe(assignment));
        return mapper.teacherView(assignment);
    }

    // ---- files -----------------------------------------------------------------------------------------

    @Transactional
    public AssignmentDtos.Response addAttachments(AuthenticatedUser teacher, UUID id, List<MultipartFile> uploaded) {
        Assignment assignment = owned(teacher, id);
        if (assignment.getStatus() != AssignmentStatus.DRAFT && !assignment.isPublished()) {
            throw new BusinessRuleException("Files can only be added to a draft or a published assignment.");
        }
        List<MultipartFile> incoming = uploaded == null ? List.of() : uploaded;
        if (incoming.isEmpty()) {
            throw new FieldValidationException("files", "Choose at least one file");
        }
        if (files.countByAssignmentId(id) + incoming.size() > uploadLimits.maxAssignmentFiles()) {
            throw new BusinessRuleException("An assignment can have at most " + uploadLimits.maxAssignmentFiles()
                    + " files.");
        }
        for (UploadService.StoredUpload stored : uploads.storeAll(incoming)) {
            files.save(new AssignmentAttachment(assignment, stored.originalName(), stored.key(),
                    stored.contentType(), stored.sizeBytes(), teacher.id()));
        }
        files.flush();
        audit.record(teacher, "ASSIGNMENT_FILE_ADDED", ENTITY, id, "Added " + incoming.size() + " file(s) to "
                + describe(assignment));
        return mapper.teacherView(assignment);
    }

    @Transactional
    public AssignmentDtos.Response removeAttachment(AuthenticatedUser teacher, UUID id, UUID attachmentId) {
        Assignment assignment = owned(teacher, id);
        if (assignment.getStatus() != AssignmentStatus.DRAFT && !assignment.isPublished()) {
            throw new BusinessRuleException("Files can only be removed from a draft or a published assignment.");
        }
        AssignmentAttachment attachment = files.findByIdAndAssignmentId(attachmentId, id)
                .orElseThrow(() -> new ResourceNotFoundException("File"));
        files.delete(attachment);
        files.flush();
        uploads.deleteAfterCommit(attachment.getStorageKey());
        audit.record(teacher, "ASSIGNMENT_FILE_REMOVED", ENTITY, id, "Removed a file from " + describe(assignment));
        return mapper.teacherView(assignment);
    }

    @Transactional(readOnly = true)
    public FileDownload downloadAttachment(AuthenticatedUser teacher, UUID id, UUID attachmentId) {
        owned(teacher, id);
        AssignmentAttachment attachment = files.findByIdAndAssignmentId(attachmentId, id)
                .orElseThrow(() -> new ResourceNotFoundException("File"));
        return open(attachment.getStorageKey(), attachment.getOriginalName(), attachment.getContentType(),
                attachment.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public FileDownload downloadSubmissionAttachment(AuthenticatedUser teacher, UUID id, UUID submissionId,
            UUID attachmentId) {
        owned(teacher, id);
        submissions.findByIdAndAssignmentId(submissionId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission"));
        SubmissionAttachment attachment = submissionFiles.findByIdAndSubmissionId(attachmentId, submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("File"));
        return open(attachment.getStorageKey(), attachment.getOriginalName(), attachment.getContentType(),
                attachment.getSizeBytes());
    }

    FileDownload open(String key, String name, String contentType, long size) {
        try {
            return new FileDownload(name, contentType, size, uploads.open(key));
        } catch (StoredFileMissingException e) {
            log.error("A stored file is missing: {}", key);
            throw new ResourceNotFoundException("File");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Could not read the stored file", e);
        }
    }

    // ---- submissions -----------------------------------------------------------------------------------

    /** Every student of the class with the state of their work, in roll-number order. */
    @Transactional(readOnly = true)
    public List<AssignmentDtos.RosterRow> roster(AuthenticatedUser teacher, UUID id) {
        Assignment assignment = owned(teacher, id);
        Map<UUID, AssignmentSubmission> byStudent = submissions.findAllByAssignmentId(id).stream()
                .collect(Collectors.toMap(s -> s.getStudent().getId(), Function.identity()));
        List<AssignmentDtos.RosterRow> rows = new java.util.ArrayList<>();
        for (StudentEnrollment enrollment : enrollments.findActiveBySectionId(assignment.getSection().getId())) {
            var student = enrollment.getStudent();
            rows.add(row(student.getId(), student.getAdmissionNumber(),
                    student.getUser().getFirstName() + " " + student.getUser().getLastName(),
                    enrollment.getRollNumber(), byStudent.remove(student.getId())));
        }
        // Students who submitted and have since left the class stay visible.
        byStudent.values().stream()
                .sorted(Comparator.comparing(AssignmentSubmission::getSubmittedAt))
                .forEach(s -> rows.add(row(s.getStudent().getId(), s.getStudent().getAdmissionNumber(),
                        s.getStudent().getUser().getFirstName() + " " + s.getStudent().getUser().getLastName(),
                        null, s)));
        return rows;
    }

    private static AssignmentDtos.RosterRow row(UUID studentId, String admission, String name, Integer roll,
            AssignmentSubmission submission) {
        return new AssignmentDtos.RosterRow(studentId, admission, name.trim(), roll,
                submission == null ? null : submission.getId(), submission == null ? null : submission.getStatus(),
                submission == null ? null : submission.getSubmittedAt(),
                submission == null ? null : submission.getMarks());
    }

    @Transactional(readOnly = true)
    public AssignmentDtos.SubmissionResponse submission(AuthenticatedUser teacher, UUID id, UUID submissionId) {
        owned(teacher, id);
        return mapper.submissionView(submissionOf(id, submissionId), false);
    }

    @Transactional
    public AssignmentDtos.SubmissionResponse review(AuthenticatedUser teacher, UUID id, UUID submissionId,
            AssignmentDtos.ReviewRequest request) {
        Assignment assignment = owned(teacher, id);
        if (!assignment.isVisibleToStudents()) {
            throw new BusinessRuleException("Only published or closed assignments can be reviewed.");
        }
        AssignmentSubmission submission = submissionOf(id, submissionId);
        OptimisticVersion.check(request.version(), submission.getVersion());
        String feedback = blankToNull(request.feedback());
        Instant now = clock.instant();
        if (request.outcome() == AssignmentDtos.ReviewOutcome.REVIEWED) {
            BigDecimal marks = request.marks();
            if (marks == null) {
                throw new FieldValidationException("marks", "Marks are required to complete a review");
            }
            if (marks.compareTo(assignment.getMaxMarks()) > 0) {
                throw new FieldValidationException("marks",
                        "Marks cannot be more than " + assignment.getMaxMarks().stripTrailingZeros().toPlainString());
            }
            submission.review(marks, feedback, teacher.id(), now);
        } else {
            if (feedback == null) {
                throw new FieldValidationException("feedback", "Say what the student should improve");
            }
            submission.requestResubmission(feedback, teacher.id(), now);
        }
        submissions.saveAndFlush(submission);
        audit.record(teacher, "SUBMISSION_REVIEWED", "AssignmentSubmission", submissionId,
                request.outcome() + " " + submission.getStudent().getAdmissionNumber() + " for "
                        + describe(assignment));
        return mapper.submissionView(submission, false);
    }

    /** Work handed in across all of the teacher's assignments, oldest first by default. */
    @Transactional(readOnly = true)
    public PageResponse<AssignmentDtos.SubmissionListItem> reviewQueue(AuthenticatedUser teacher,
            UUID assignmentId, SubmissionStatus status, boolean pendingOnly, Pageable pageable) {
        Specification<AssignmentSubmission> spec = Specification
                .<AssignmentSubmission>where((root, query, cb) -> cb.equal(root.get("assignment").get("schoolId"),
                        teacher.schoolId()))
                .and((root, query, cb) -> cb.equal(root.get("assignment").get("teacherId"), teacher.id()))
                .and((root, query, cb) -> root.get("assignment").get("status")
                        .in(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED))
                .and(SearchSpecs.equalTo("status", status));
        if (assignmentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignment").get("id"), assignmentId));
        }
        if (pendingOnly) {
            spec = spec.and((root, query, cb) -> root.get("status")
                    .in(SubmissionStatus.SUBMITTED, SubmissionStatus.LATE));
        }
        Page<AssignmentSubmission> page = submissions.findAll(spec, pageable);
        return PageResponse.from(page, s -> mapper.listItem(s, false));
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private Assignment owned(AuthenticatedUser teacher, UUID id) {
        return assignments.findByIdAndSchoolId(id, teacher.schoolId())
                .filter(assignment -> assignment.getTeacherId().equals(teacher.id()))
                .orElseThrow(() -> new ResourceNotFoundException("Assignment"));
    }

    private AssignmentSubmission submissionOf(UUID assignmentId, UUID submissionId) {
        return submissions.findByIdAndAssignmentId(submissionId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission"));
    }

    private void requireOpenYear(Section section) {
        if (section.getAcademicYear().getStatus() != AcademicYearStatus.ACTIVE) {
            throw new BusinessRuleException("Assignments can only be managed in the active academic year.");
        }
    }

    private void requireFuture(Instant dueAt) {
        if (!dueAt.isAfter(clock.instant())) {
            throw new FieldValidationException("dueAt", "The due date must be in the future");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String describe(Assignment assignment) {
        return "\"" + assignment.getTitle() + "\" ("
                + assignment.getSection().getGradeLevel().getName() + " - " + assignment.getSection().getName()
                + ", " + assignment.getSubject().getCode() + ")";
    }
}
