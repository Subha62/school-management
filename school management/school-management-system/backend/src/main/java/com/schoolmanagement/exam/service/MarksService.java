package com.schoolmanagement.exam.service;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.FieldError;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.entity.Exam;
import com.schoolmanagement.exam.entity.ExamComment;
import com.schoolmanagement.exam.entity.ExamStatus;
import com.schoolmanagement.exam.entity.ExamSubject;
import com.schoolmanagement.exam.entity.StudentMark;
import com.schoolmanagement.exam.repository.ExamCommentRepository;
import com.schoolmanagement.exam.repository.ExamRepository;
import com.schoolmanagement.exam.repository.ExamSubjectRepository;
import com.schoolmanagement.exam.repository.StudentMarkRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The teacher's side of examinations: which exams they have work in, entering marks for the subject and class they
 * teach, and the class teacher's overall comments. Authority is re-derived from the subject assignments and class
 * teacher on every request; marks can only change while the exam is open for marks.
 */
@Service
public class MarksService {

    private static final String ENTITY = "Exam";

    private final ExamRepository exams;
    private final ExamSubjectRepository examSubjects;
    private final StudentMarkRepository marks;
    private final ExamCommentRepository comments;
    private final SectionRepository sections;
    private final SectionSubjectRepository sectionSubjects;
    private final StudentEnrollmentRepository enrollments;
    private final AuditEventPublisher audit;

    public MarksService(ExamRepository exams, ExamSubjectRepository examSubjects, StudentMarkRepository marks,
            ExamCommentRepository comments, SectionRepository sections, SectionSubjectRepository sectionSubjects,
            StudentEnrollmentRepository enrollments, AuditEventPublisher audit) {
        this.exams = exams;
        this.examSubjects = examSubjects;
        this.marks = marks;
        this.comments = comments;
        this.sections = sections;
        this.sectionSubjects = sectionSubjects;
        this.enrollments = enrollments;
        this.audit = audit;
    }

    // ---- my exams ---------------------------------------------------------------------------------------

    private record Pair(UUID yearId, UUID gradeId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamDtos.TeacherExam> myExams(AuthenticatedUser teacher, Pageable pageable) {
        List<SectionSubject> teaching = sectionSubjects.findAllByTeacherIdAndActiveTrueAndSchoolId(teacher.id(),
                teacher.schoolId());
        List<Section> classTeacherOf = sections.findAllByClassTeacherIdAndSchoolId(teacher.id(), teacher.schoolId());

        Map<Pair, Set<UUID>> subjectsByPair = new HashMap<>();
        for (SectionSubject a : teaching) {
            subjectsByPair.computeIfAbsent(new Pair(a.getAcademicYear().getId(), a.getSection().getGradeLevel().getId()),
                    k -> new HashSet<>()).add(a.getSubject().getId());
        }
        Set<Pair> commentPairs = classTeacherOf.stream()
                .map(s -> new Pair(s.getAcademicYear().getId(), s.getGradeLevel().getId()))
                .collect(Collectors.toSet());
        if (subjectsByPair.isEmpty() && commentPairs.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0, true, true);
        }

        Specification<Exam> spec = (root, query, cb) -> {
            List<Predicate> any = new ArrayList<>();
            subjectsByPair.forEach((pair, subjectIds) -> {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<ExamSubject> es = sub.from(ExamSubject.class);
                sub.select(es.get("id")).where(cb.equal(es.get("exam"), root),
                        es.get("subject").get("id").in(subjectIds));
                any.add(cb.and(cb.equal(root.get("academicYear").get("id"), pair.yearId()),
                        cb.equal(root.get("gradeLevel").get("id"), pair.gradeId()), cb.exists(sub)));
            });
            commentPairs.forEach(pair -> any.add(cb.and(cb.equal(root.get("academicYear").get("id"), pair.yearId()),
                    cb.equal(root.get("gradeLevel").get("id"), pair.gradeId()))));
            return cb.and(cb.equal(root.get("schoolId"), teacher.schoolId()),
                    cb.notEqual(root.get("status"), ExamStatus.DRAFT), cb.or(any.toArray(new Predicate[0])));
        };
        Page<Exam> page = exams.findAll(spec, pageable);

        Map<UUID, List<ExamSubject>> subjectsByExam = page.isEmpty() ? Map.of()
                : examSubjects.findAllByExamIdInOrderByCreatedAtAscIdAsc(page.getContent().stream().map(Exam::getId)
                        .toList()).stream().collect(Collectors.groupingBy(s -> s.getExam().getId()));
        Map<UUID, Long> rosterSizes = new HashMap<>();
        List<ExamDtos.TeacherExam> views = new ArrayList<>();
        for (Exam exam : page.getContent()) {
            Map<String, Integer> entered = new HashMap<>();
            for (Object[] row : marks.countEntered(exam.getId(), exam.getAcademicYear().getId(),
                    exam.getGradeLevel().getId())) {
                entered.put(row[0] + ":" + row[1], ((Number) row[2]).intValue());
            }
            Map<UUID, ExamSubject> bySubject = subjectsByExam.getOrDefault(exam.getId(), List.of()).stream()
                    .collect(Collectors.toMap(s -> s.getSubject().getId(), Function.identity()));
            List<ExamDtos.Task> tasks = new ArrayList<>();
            for (SectionSubject a : teaching) {
                ExamSubject es = bySubject.get(a.getSubject().getId());
                if (es == null || !a.getAcademicYear().getId().equals(exam.getAcademicYear().getId())
                        || !a.getSection().getGradeLevel().getId().equals(exam.getGradeLevel().getId())) {
                    continue;
                }
                UUID sectionId = a.getSection().getId();
                int expected = rosterSizes.computeIfAbsent(sectionId,
                        id -> enrollments.countBySectionIdAndStatus(id, EnrollmentStatus.ACTIVE)).intValue();
                int done = Math.min(expected, entered.getOrDefault(es.getId() + ":" + sectionId, 0));
                tasks.add(new ExamDtos.Task(es.getId(), a.getSubject().getName(), sectionId,
                        ResultService.sectionName(a.getSection()), es.getMaxMarks(), es.getPassMarks(), expected, done));
            }
            tasks.sort(java.util.Comparator.comparing(ExamDtos.Task::sectionName)
                    .thenComparing(ExamDtos.Task::subjectName));
            List<ExamDtos.SectionRef> commentSections = classTeacherOf.stream()
                    .filter(s -> s.getAcademicYear().getId().equals(exam.getAcademicYear().getId())
                            && s.getGradeLevel().getId().equals(exam.getGradeLevel().getId()))
                    .map(s -> new ExamDtos.SectionRef(s.getId(), ResultService.sectionName(s)))
                    .sorted(java.util.Comparator.comparing(ExamDtos.SectionRef::name)).toList();
            views.add(new ExamDtos.TeacherExam(exam.getId(), exam.getName(), exam.getStatus(),
                    exam.getAcademicYear().getName(), exam.getGradeLevel().getName(), exam.getStartDate(),
                    exam.getEndDate(), tasks, commentSections));
        }
        return new PageResponse<>(views, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    // ---- marks ------------------------------------------------------------------------------------------

    private record Access(Exam exam, ExamSubject examSubject, Section section) {
    }

    private Access authorize(AuthenticatedUser teacher, UUID examId, UUID examSubjectId, UUID sectionId,
            boolean forWrite) {
        Exam exam = (forWrite ? exams.lockForMarks(examId, teacher.schoolId())
                : exams.findByIdAndSchoolId(examId, teacher.schoolId()))
                .filter(e -> e.getStatus() != ExamStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Exam"));
        ExamSubject examSubject = exam.getSubjects().stream().filter(s -> s.getId().equals(examSubjectId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Exam subject"));
        Section section = inExam(exam, sectionId, teacher.schoolId());
        if (!sectionSubjects.existsBySectionIdAndSubjectIdAndTeacherIdAndActiveTrue(section.getId(),
                examSubject.getSubject().getId(), teacher.id())) {
            throw new AccessDeniedException("Not the teacher of this subject in this class");
        }
        return new Access(exam, examSubject, section);
    }

    private Section inExam(Exam exam, UUID sectionId, UUID schoolId) {
        return sections.findByIdAndSchoolId(sectionId, schoolId)
                .filter(s -> s.getAcademicYear().getId().equals(exam.getAcademicYear().getId())
                        && s.getGradeLevel().getId().equals(exam.getGradeLevel().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
    }

    @Transactional(readOnly = true)
    public ExamDtos.MarksSheet sheet(AuthenticatedUser teacher, UUID examId, UUID examSubjectId, UUID sectionId) {
        return sheetOf(authorize(teacher, examId, examSubjectId, sectionId, false));
    }

    private ExamDtos.MarksSheet sheetOf(Access access) {
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(access.section().getId());
        Map<UUID, StudentMark> entered = existing(access.examSubject(), roster);
        List<ExamDtos.MarkRow> rows = roster.stream().map(e -> {
            StudentMark mark = entered.get(e.getStudent().getId());
            return new ExamDtos.MarkRow(e.getStudent().getId(), e.getStudent().getAdmissionNumber(),
                    ResultService.displayName(e.getStudent()), e.getRollNumber(), mark == null ? null : mark.getMarks(),
                    mark != null && mark.isAbsent(), mark == null ? null : mark.getRemarks(), mark != null);
        }).toList();
        Exam exam = access.exam();
        ExamSubject es = access.examSubject();
        return new ExamDtos.MarksSheet(exam.getId(), exam.getName(), exam.getStatus(), es.getId(),
                es.getSubject().getName(), access.section().getId(), ResultService.sectionName(access.section()),
                es.getMaxMarks(), es.getPassMarks(), exam.isOpen(), rows);
    }

    private Map<UUID, StudentMark> existing(ExamSubject examSubject, List<StudentEnrollment> roster) {
        if (roster.isEmpty()) {
            return Map.of();
        }
        return marks.findAllByExamSubjectIdAndStudentIdIn(examSubject.getId(),
                roster.stream().map(e -> e.getStudent().getId()).toList()).stream()
                .collect(Collectors.toMap(m -> m.getStudent().getId(), Function.identity()));
    }

    @Transactional
    public ExamDtos.MarksSheet save(AuthenticatedUser teacher, UUID examId, UUID examSubjectId, UUID sectionId,
            ExamDtos.SaveMarksRequest request) {
        Access access = authorize(teacher, examId, examSubjectId, sectionId, true);
        if (!access.exam().isOpen()) {
            throw new BusinessRuleException("Marks can only be entered while the exam is open for marks.");
        }
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(access.section().getId());
        Map<UUID, StudentEnrollment> inClass = roster.stream()
                .collect(Collectors.toMap(e -> e.getStudent().getId(), Function.identity()));
        BigDecimal max = access.examSubject().getMaxMarks();

        List<FieldError> problems = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < request.entries().size(); i++) {
            ExamDtos.MarkEntry entry = request.entries().get(i);
            if (!seen.add(entry.studentId())) {
                problems.add(new FieldError("entries[" + i + "].studentId", "This student is listed twice"));
            } else if (!inClass.containsKey(entry.studentId())) {
                problems.add(new FieldError("entries[" + i + "].studentId", "This student is not in the class"));
            }
            if (entry.absent() && entry.marks() != null) {
                problems.add(new FieldError("entries[" + i + "].marks", "An absent student cannot have marks"));
            } else if (entry.marks() != null && entry.marks().compareTo(max) > 0) {
                problems.add(new FieldError("entries[" + i + "].marks",
                        "Marks cannot be more than " + max.stripTrailingZeros().toPlainString()));
            }
        }
        if (!problems.isEmpty()) {
            throw new FieldValidationException(problems);
        }

        Map<UUID, StudentMark> current = existing(access.examSubject(), roster);
        int changed = 0;
        StringBuilder detail = new StringBuilder();
        for (ExamDtos.MarkEntry entry : request.entries()) {
            StudentEnrollment enrollment = inClass.get(entry.studentId());
            StudentMark mark = current.get(entry.studentId());
            boolean clears = !entry.absent() && entry.marks() == null;
            String before = describe(mark);
            String beforeRemarks = mark == null ? null : mark.getRemarks();
            if (clears) {
                if (mark != null) {
                    marks.delete(mark);
                    changed++;
                    note(detail, enrollment, before, "cleared");
                }
                continue;
            }
            BigDecimal value = entry.marks() == null ? null : entry.marks().setScale(2);
            String remarks = entry.remarks() == null || entry.remarks().isBlank() ? null : entry.remarks().trim();
            StudentMark target = mark != null ? mark
                    : new StudentMark(access.examSubject(), enrollment.getStudent(), access.section());
            target.record(value, entry.absent(), remarks, teacher.id());
            String after = describe(target);
            if (mark == null || !before.equals(after) || !java.util.Objects.equals(beforeRemarks, remarks)) {
                changed++;
                note(detail, enrollment, before, after);
            }
            marks.save(target);
        }
        marks.flush();
        if (changed > 0) {
            audit.record(teacher, "MARKS_SAVED", ENTITY, access.exam().getId(),
                    access.examSubject().getSubject().getName() + " " + ResultService.sectionName(access.section())
                            + ": " + changed + " change(s). " + detail);
        }
        return sheetOf(access);
    }

    private static String describe(StudentMark mark) {
        return mark == null ? "none" : mark.isAbsent() ? "absent" : mark.getMarks().stripTrailingZeros().toPlainString();
    }

    private static void note(StringBuilder detail, StudentEnrollment enrollment, String before, String after) {
        if (detail.length() < 400) {
            detail.append(enrollment.getStudent().getAdmissionNumber()).append(' ').append(before).append("->")
                    .append(after).append("; ");
        }
    }

    // ---- class teacher comments -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExamDtos.CommentSheet commentSheet(AuthenticatedUser teacher, UUID examId, UUID sectionId) {
        Exam exam = exams.findByIdAndSchoolId(examId, teacher.schoolId()).filter(e -> e.getStatus() != ExamStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Exam"));
        Section section = inExam(exam, sectionId, teacher.schoolId());
        requireClassTeacher(teacher, section);
        List<StudentEnrollment> roster = enrollments.findActiveBySectionId(section.getId());
        Map<UUID, ExamComment> written = roster.isEmpty() ? Map.of()
                : comments.findAllByExamIdAndStudentIdIn(examId,
                        roster.stream().map(e -> e.getStudent().getId()).toList()).stream()
                        .collect(Collectors.toMap(c -> c.getStudent().getId(), Function.identity()));
        Map<UUID, ExamDtos.CommentRow> rows = new LinkedHashMap<>();
        for (StudentEnrollment e : roster) {
            ExamComment c = written.get(e.getStudent().getId());
            rows.put(e.getStudent().getId(), new ExamDtos.CommentRow(e.getStudent().getId(),
                    e.getStudent().getAdmissionNumber(), ResultService.displayName(e.getStudent()), e.getRollNumber(),
                    c == null ? null : c.getComment()));
        }
        return new ExamDtos.CommentSheet(examId, exam.getName(), exam.getStatus(), section.getId(),
                ResultService.sectionName(section), exam.isOpen(), List.copyOf(rows.values()));
    }

    @Transactional
    public ExamDtos.CommentRow saveComment(AuthenticatedUser teacher, UUID examId, UUID studentId,
            ExamDtos.SaveCommentRequest request) {
        Exam exam = exams.lockForMarks(examId, teacher.schoolId()).filter(e -> e.getStatus() != ExamStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Exam"));
        StudentEnrollment enrollment = enrollments
                .findActiveInYearAndGrade(exam.getAcademicYear().getId(), exam.getGradeLevel().getId()).stream()
                .filter(e -> e.getStudent().getId().equals(studentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Student"));
        requireClassTeacher(teacher, enrollment.getSection());
        if (!exam.isOpen()) {
            throw new BusinessRuleException("Comments can only be changed while the exam is open for marks.");
        }
        String text = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        ExamComment existing = comments.findByExamIdAndStudentId(examId, studentId).orElse(null);
        if (text == null) {
            if (existing != null) {
                comments.delete(existing);
            }
        } else if (existing == null) {
            comments.save(new ExamComment(exam, enrollment.getStudent(), enrollment.getSection(), text, teacher.id()));
        } else {
            existing.write(text, teacher.id());
        }
        comments.flush();
        audit.record(teacher, "EXAM_COMMENT_SAVED", ENTITY, examId,
                "Comment for " + enrollment.getStudent().getAdmissionNumber() + (text == null ? " removed" : " saved"));
        return new ExamDtos.CommentRow(studentId, enrollment.getStudent().getAdmissionNumber(),
                ResultService.displayName(enrollment.getStudent()), enrollment.getRollNumber(), text);
    }

    private static void requireClassTeacher(AuthenticatedUser teacher, Section section) {
        if (!teacher.id().equals(section.getClassTeacherId())) {
            throw new AccessDeniedException("Not the class teacher of this class");
        }
    }
}
