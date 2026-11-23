package com.schoolmanagement.assignment.service;

import com.schoolmanagement.assignment.dto.AssignmentDtos;
import com.schoolmanagement.assignment.entity.Assignment;
import com.schoolmanagement.assignment.entity.AssignmentAttachment;
import com.schoolmanagement.assignment.entity.AssignmentSubmission;
import com.schoolmanagement.assignment.entity.FileAttachment;
import com.schoolmanagement.assignment.entity.SubmissionAttachment;
import com.schoolmanagement.assignment.entity.SubmissionStatus;
import com.schoolmanagement.assignment.repository.AssignmentAttachmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionAttachmentRepository;
import com.schoolmanagement.assignment.repository.SubmissionRepository;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds the API representation of assignments and submissions, loading names, files and counts in batches. */
@Component
public class AssignmentMapper {

    private final AssignmentAttachmentRepository assignmentFiles;
    private final SubmissionAttachmentRepository submissionFiles;
    private final SubmissionRepository submissions;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherDirectory directory;

    public AssignmentMapper(AssignmentAttachmentRepository assignmentFiles,
            SubmissionAttachmentRepository submissionFiles, SubmissionRepository submissions,
            StudentEnrollmentRepository enrollments, TeacherDirectory directory) {
        this.assignmentFiles = assignmentFiles;
        this.submissionFiles = submissionFiles;
        this.submissions = submissions;
        this.enrollments = enrollments;
        this.directory = directory;
    }

    public static List<AssignmentDtos.AttachmentResponse> files(List<? extends FileAttachment> attachments) {
        return attachments.stream()
                .map(file -> new AssignmentDtos.AttachmentResponse(file.getId(), file.getOriginalName(),
                        file.getContentType(), file.getSizeBytes(), file.getCreatedAt()))
                .toList();
    }

    public List<AssignmentDtos.Response> teacherViews(Collection<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = assignments.stream().map(Assignment::getId).toList();
        Map<UUID, List<AssignmentAttachment>> filesById = assignmentFiles.findAllByAssignmentIdIn(ids).stream()
                .collect(Collectors.groupingBy(file -> file.getAssignment().getId()));
        Map<UUID, Map<SubmissionStatus, Integer>> byStatus = new HashMap<>();
        for (Object[] row : submissions.countByStatus(ids)) {
            byStatus.computeIfAbsent((UUID) row[0], key -> new HashMap<>())
                    .put((SubmissionStatus) row[1], ((Number) row[2]).intValue());
        }
        Map<UUID, Integer> enrolled = new HashMap<>();
        Map<UUID, String> names = directory.displayNames(
                assignments.stream().map(Assignment::getTeacherId).collect(Collectors.toSet()));
        return assignments.stream().map(assignment -> {
            Map<SubmissionStatus, Integer> counts = byStatus.getOrDefault(assignment.getId(), Map.of());
            int submitted = counts.values().stream().mapToInt(Integer::intValue).sum();
            int awaiting = counts.getOrDefault(SubmissionStatus.SUBMITTED, 0)
                    + counts.getOrDefault(SubmissionStatus.LATE, 0);
            int roster = enrolled.computeIfAbsent(assignment.getSection().getId(),
                    id -> (int) enrollments.countBySectionIdAndStatus(id, EnrollmentStatus.ACTIVE));
            return teacherView(assignment, files(filesById.getOrDefault(assignment.getId(), List.of())),
                    new AssignmentDtos.Counts(roster, submitted, awaiting,
                            counts.getOrDefault(SubmissionStatus.REVIEWED, 0)),
                    names.get(assignment.getTeacherId()));
        }).toList();
    }

    public AssignmentDtos.Response teacherView(Assignment assignment) {
        return teacherViews(List.of(assignment)).get(0);
    }

    private static AssignmentDtos.Response teacherView(Assignment a, List<AssignmentDtos.AttachmentResponse> files,
            AssignmentDtos.Counts counts, String teacherName) {
        return new AssignmentDtos.Response(a.getId(), a.getSection().getId(),
                a.getSection().getGradeLevel().getName() + " - " + a.getSection().getName(),
                a.getAcademicYear().getName(), a.getSubject().getId(), a.getSubject().getName(), a.getTeacherId(),
                teacherName, a.getTitle(), a.getDescription(), a.getDueAt(), a.getMaxMarks(), a.isAllowLate(),
                a.getStatus(), a.getPublishedAt(), a.getClosedAt(), files, counts, a.getVersion());
    }

    public List<AssignmentDtos.AttachmentResponse> assignmentFiles(UUID assignmentId) {
        return files(assignmentFiles.findAllByAssignmentIdOrderByCreatedAtAsc(assignmentId));
    }

    public String teacherName(UUID teacherId) {
        return directory.displayNames(Set.of(teacherId)).get(teacherId);
    }

    /**
     * A submission as the teacher sees it, or as the student sees it: a student gets the marks only once the work
     * is reviewed and the feedback only after a review or a resubmission request.
     */
    public AssignmentDtos.SubmissionResponse submissionView(AssignmentSubmission submission, boolean forStudent) {
        List<SubmissionAttachment> attachments = submissionFiles
                .findAllBySubmissionIdOrderByCreatedAtAsc(submission.getId());
        Map<UUID, String> reviewer = submission.getReviewedBy() == null ? Map.of()
                : directory.displayNames(Set.of(submission.getReviewedBy()));
        boolean reviewed = submission.getStatus() == SubmissionStatus.REVIEWED;
        boolean feedbackVisible = !forStudent || reviewed
                || submission.getStatus() == SubmissionStatus.RESUBMISSION_REQUESTED;
        StudentProfile student = submission.getStudent();
        return new AssignmentDtos.SubmissionResponse(submission.getId(), submission.getAssignment().getId(),
                student.getId(), student.getAdmissionNumber(),
                (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim(),
                submission.getStatus(), submission.getTextAnswer(), submission.getSubmittedAt(),
                forStudent && !reviewed ? null : submission.getMarks(),
                feedbackVisible ? submission.getFeedback() : null, submission.getReviewedAt(),
                submission.getReviewedBy() == null ? null : reviewer.get(submission.getReviewedBy()),
                files(attachments), submission.getVersion());
    }

    public AssignmentDtos.SubmissionListItem listItem(AssignmentSubmission s, boolean forStudent) {
        Assignment a = s.getAssignment();
        StudentProfile student = s.getStudent();
        boolean reviewed = s.getStatus() == SubmissionStatus.REVIEWED;
        return new AssignmentDtos.SubmissionListItem(s.getId(), a.getId(), a.getTitle(), a.getSubject().getName(),
                a.getSection().getGradeLevel().getName() + " - " + a.getSection().getName(), student.getId(),
                student.getAdmissionNumber(),
                (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim(), s.getStatus(),
                s.getSubmittedAt(), a.getDueAt(), forStudent && !reviewed ? null : s.getMarks(), a.getMaxMarks());
    }
}
