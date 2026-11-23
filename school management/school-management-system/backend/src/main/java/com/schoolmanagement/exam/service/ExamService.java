package com.schoolmanagement.exam.service;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.GradeLevelRepository;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldError;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.entity.Exam;
import com.schoolmanagement.exam.entity.ExamStatus;
import com.schoolmanagement.exam.entity.ExamSubject;
import com.schoolmanagement.exam.repository.ExamRepository;
import com.schoolmanagement.exam.repository.ExamSubjectRepository;
import com.schoolmanagement.exam.repository.StudentMarkRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exam set-up and the lifecycle that only administrators control: open for marks, lock, reopen and publish.
 * Locking and publishing wait for any marks being saved and refuse to proceed while marks are missing or the grade
 * scale has gaps, so a published result is always complete.
 */
@Service
public class ExamService {

    private static final String ENTITY = "Exam";

    private final ExamRepository exams;
    private final ExamSubjectRepository examSubjects;
    private final StudentMarkRepository marks;
    private final AcademicYearRepository years;
    private final GradeLevelRepository grades;
    private final SubjectRepository subjects;
    private final ExamScope scopes;
    private final GradeRuleService gradeRules;
    private final ResultService results;
    private final TeacherDirectory teachers;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public ExamService(ExamRepository exams, ExamSubjectRepository examSubjects, StudentMarkRepository marks,
            AcademicYearRepository years, GradeLevelRepository grades, SubjectRepository subjects, ExamScope scopes,
            GradeRuleService gradeRules, ResultService results, TeacherDirectory teachers, AuditEventPublisher audit,
            Clock clock) {
        this.exams = exams;
        this.examSubjects = examSubjects;
        this.marks = marks;
        this.years = years;
        this.grades = grades;
        this.subjects = subjects;
        this.scopes = scopes;
        this.gradeRules = gradeRules;
        this.results = results;
        this.teachers = teachers;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- reading ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ExamDtos.ExamResponse> list(UUID schoolId, UUID yearId, UUID gradeId, ExamStatus status,
            String search, Pageable pageable) {
        Specification<Exam> spec = Specification.<Exam>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("status", status))
                .and(SearchSpecs.containsIgnoreCase("name", search));
        if (yearId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), yearId));
        }
        if (gradeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("gradeLevel").get("id"), gradeId));
        }
        Page<Exam> page = exams.findAll(spec, pageable);
        List<ExamDtos.ExamResponse> views = views(page.getContent());
        return new PageResponse<>(views, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public ExamDtos.ExamResponse get(UUID schoolId, UUID id) {
        return views(List.of(require(schoolId, id))).get(0);
    }

    Exam require(UUID schoolId, UUID id) {
        return exams.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Exam"));
    }

    List<ExamDtos.ExamResponse> views(List<Exam> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<ExamSubject>> byExam = examSubjects
                .findAllByExamIdInOrderByCreatedAtAscIdAsc(list.stream().map(Exam::getId).toList()).stream()
                .collect(Collectors.groupingBy(s -> s.getExam().getId()));
        return list.stream().map(exam -> view(exam, byExam.getOrDefault(exam.getId(), List.of()).stream()
                .sorted(Comparator.comparing(s -> s.getSubject().getName())).toList())).toList();
    }

    private static ExamDtos.ExamResponse view(Exam e, List<ExamSubject> subjects) {
        return new ExamDtos.ExamResponse(e.getId(), e.getAcademicYear().getId(), e.getAcademicYear().getName(),
                e.getGradeLevel().getId(), e.getGradeLevel().getName(), e.getName(), e.getStartDate(), e.getEndDate(),
                e.getStatus(),
                subjects.stream().map(s -> new ExamDtos.ExamSubjectResponse(s.getId(), s.getSubject().getId(),
                        s.getSubject().getCode(), s.getSubject().getName(), s.getMaxMarks(), s.getPassMarks(),
                        s.getExamDate())).toList(),
                e.getLockedAt(), e.getPublishedAt(), e.getVersion());
    }

    // ---- set-up -----------------------------------------------------------------------------------------

    @Transactional
    public ExamDtos.ExamResponse create(AuthenticatedUser actor, ExamDtos.ExamRequest request) {
        List<FieldError> missing = new ArrayList<>();
        if (request.academicYearId() == null) {
            missing.add(new FieldError("academicYearId", "Academic year is required"));
        }
        if (request.gradeLevelId() == null) {
            missing.add(new FieldError("gradeLevelId", "Grade is required"));
        }
        if (!missing.isEmpty()) {
            throw new FieldValidationException(missing);
        }
        AcademicYear year = years.findByIdAndSchoolId(request.academicYearId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic year"));
        if (year.getStatus() != AcademicYearStatus.ACTIVE) {
            throw new BusinessRuleException("Exams can only be created in the active academic year.");
        }
        GradeLevel grade = grades.findByIdAndSchoolId(request.gradeLevelId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Grade"));
        String name = request.name().trim();
        if (exams.existsBySchoolIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCase(actor.schoolId(), year.getId(),
                grade.getId(), name)) {
            throw new ConflictException("This grade already has an exam named \"" + name + "\" this year.");
        }
        checkDates(request);
        Exam exam = new Exam(actor.schoolId(), year, grade, name, request.startDate(), request.endDate());
        applySubjects(actor.schoolId(), exam, request);
        exams.saveAndFlush(exam);
        audit.record(actor, "EXAM_CREATED", ENTITY, exam.getId(), describe(exam));
        return get(actor.schoolId(), exam.getId());
    }

    @Transactional
    public ExamDtos.ExamResponse update(AuthenticatedUser actor, UUID id, ExamDtos.ExamRequest request) {
        Exam exam = exams.lockForUpdate(id, actor.schoolId()).orElseThrow(() -> new ResourceNotFoundException("Exam"));
        OptimisticVersion.check(request.version(), exam.getVersion());
        if (!exam.isDraft()) {
            throw new BusinessRuleException("Only a draft exam can be edited.");
        }
        String name = request.name().trim();
        if (exams.existsBySchoolIdAndAcademicYearIdAndGradeLevelIdAndNameIgnoreCaseAndIdNot(actor.schoolId(),
                exam.getAcademicYear().getId(), exam.getGradeLevel().getId(), name, id)) {
            throw new ConflictException("This grade already has an exam named \"" + name + "\" this year.");
        }
        checkDates(request);
        exam.rename(name, request.startDate(), request.endDate());
        applySubjects(actor.schoolId(), exam, request);
        exams.saveAndFlush(exam);
        audit.record(actor, "EXAM_UPDATED", ENTITY, id, describe(exam));
        return get(actor.schoolId(), id);
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        Exam exam = exams.lockForUpdate(id, actor.schoolId()).orElseThrow(() -> new ResourceNotFoundException("Exam"));
        if (!exam.isDraft()) {
            throw new BusinessRuleException("Only a draft exam can be deleted.");
        }
        exams.delete(exam);
        audit.record(actor, "EXAM_DELETED", ENTITY, id, describe(exam));
    }

    private void applySubjects(UUID schoolId, Exam exam, ExamDtos.ExamRequest request) {
        List<FieldError> problems = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Map<UUID, Subject> loaded = new HashMap<>();
        for (ExamDtos.ExamSubjectRequest line : request.subjects()) {
            if (!seen.add(line.subjectId())) {
                problems.add(new FieldError("subjects", "Each subject can be added only once"));
                continue;
            }
            Subject subject = subjects.findByIdAndSchoolId(line.subjectId(), schoolId).orElse(null);
            if (subject == null) {
                problems.add(new FieldError("subjects", "A selected subject does not exist"));
                continue;
            }
            if (!subject.isActive()) {
                problems.add(new FieldError("subjects", subject.getName() + " is inactive"));
            }
            if (line.passMarks().compareTo(line.maxMarks()) > 0) {
                problems.add(new FieldError("subjects",
                        "Pass marks of " + subject.getName() + " cannot exceed its maximum marks"));
            }
            if (line.examDate() != null && exam.getStartDate() != null && exam.getEndDate() != null
                    && (line.examDate().isBefore(exam.getStartDate()) || line.examDate().isAfter(exam.getEndDate()))) {
                problems.add(new FieldError("subjects", "The date of " + subject.getName()
                        + " is outside the exam dates"));
            }
            loaded.put(subject.getId(), subject);
        }
        if (!problems.isEmpty()) {
            throw new FieldValidationException(problems);
        }
        exam.getSubjects().removeIf(existing -> !seen.contains(existing.getSubject().getId()));
        Map<UUID, ExamSubject> current = exam.getSubjects().stream()
                .collect(Collectors.toMap(s -> s.getSubject().getId(), s -> s));
        for (ExamDtos.ExamSubjectRequest line : request.subjects()) {
            ExamSubject existing = current.get(line.subjectId());
            if (existing != null) {
                existing.update(line.maxMarks(), line.passMarks(), line.examDate());
            } else {
                exam.getSubjects().add(new ExamSubject(exam, loaded.get(line.subjectId()), line.maxMarks(),
                        line.passMarks(), line.examDate()));
            }
        }
    }

    private static void checkDates(ExamDtos.ExamRequest request) {
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new FieldValidationException("endDate", "The end date cannot be before the start date");
        }
    }

    // ---- lifecycle --------------------------------------------------------------------------------------

    @Transactional
    public ExamDtos.ExamResponse open(AuthenticatedUser actor, UUID id) {
        Exam exam = exams.lockForUpdate(id, actor.schoolId()).orElseThrow(() -> new ResourceNotFoundException("Exam"));
        ExamScope.Scope scope = scopes.of(exam);
        Set<UUID> examSubjectIds = exam.getSubjects().stream().map(s -> s.getSubject().getId())
                .collect(Collectors.toSet());
        if (scope.taught().stream().noneMatch(a -> examSubjectIds.contains(a.getSubject().getId()))) {
            throw new BusinessRuleException(
                    "None of the classes of this grade are taught these subjects yet. Assign subjects and teachers first.");
        }
        exam.open();
        return transitioned(actor, exam, "EXAM_OPENED");
    }

    @Transactional
    public ExamDtos.ExamResponse lock(AuthenticatedUser actor, UUID id) {
        Exam exam = exams.lockForUpdate(id, actor.schoolId()).orElseThrow(() -> new ResourceNotFoundException("Exam"));
        if (exam.getStatus() != ExamStatus.OPEN_FOR_MARKS) {
            throw new BusinessRuleException("Only an exam that is open for marks can be locked.");
        }
        requireReady(exam);
        exam.lock(clock.instant(), actor.id());
        return transitioned(actor, exam, "EXAM_LOCKED");
    }

    @Transactional
    public ExamDtos.ExamResponse reopen(AuthenticatedUser actor, UUID id) {
        Exam exam = exams.lockForUpdate(id, actor.schoolId()).orElseThrow(() -> new ResourceNotFoundException("Exam"));
        exam.reopen();
        return transitioned(actor, exam, "EXAM_REOPENED");
    }

    @Transactional
    public ExamDtos.ExamResponse publish(AuthenticatedUser actor, UUID id) {
        Exam exam = exams.lockForUpdate(id, actor.schoolId()).orElseThrow(() -> new ResourceNotFoundException("Exam"));
        if (exam.getStatus() != ExamStatus.LOCKED) {
            throw new BusinessRuleException("Lock the exam before publishing it.");
        }
        requireReady(exam);
        int count = results.freeze(exam);
        exam.publish(clock.instant(), actor.id());
        audit.record(actor, "EXAM_PUBLISHED", ENTITY, id, describe(exam) + " (" + count + " results)");
        exams.saveAndFlush(exam);
        return get(actor.schoolId(), id);
    }

    private ExamDtos.ExamResponse transitioned(AuthenticatedUser actor, Exam exam, String action) {
        exams.saveAndFlush(exam);
        audit.record(actor, action, ENTITY, exam.getId(), describe(exam));
        return get(actor.schoolId(), exam.getId());
    }

    /** Every enrolled student marked in every subject their class takes, and a grade for every percentage. */
    private void requireReady(Exam exam) {
        ExamDtos.ProgressResponse progress = progressOf(exam);
        if (progress.rows().stream().mapToInt(ExamDtos.ProgressRow::expected).sum() == 0) {
            throw new BusinessRuleException("There are no enrolled students to mark in this grade.");
        }
        if (progress.missing() > 0) {
            throw new BusinessRuleException("Marks are missing for " + progress.missing()
                    + " student entries. Check the progress table.");
        }
        gradeRules.requireCoverage(exam.getSchoolId());
    }

    // ---- progress ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExamDtos.ProgressResponse progress(UUID schoolId, UUID id) {
        return progressOf(require(schoolId, id));
    }

    ExamDtos.ProgressResponse progressOf(Exam exam) {
        ExamScope.Scope scope = scopes.of(exam);
        Map<UUID, ExamSubject> bySubject = exam.getSubjects().stream()
                .collect(Collectors.toMap(s -> s.getSubject().getId(), s -> s));
        Map<String, Integer> entered = new HashMap<>();
        for (Object[] row : marks.countEntered(exam.getId(), exam.getAcademicYear().getId(),
                exam.getGradeLevel().getId())) {
            entered.put(row[0] + ":" + row[1], ((Number) row[2]).intValue());
        }
        Map<UUID, String> teacherNames = teachers.displayNames(scope.taught().stream()
                .map(SectionSubject::getTeacherId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        List<ExamDtos.ProgressRow> rows = new ArrayList<>();
        int missing = 0;
        for (SectionSubject taught : scope.taught()) {
            ExamSubject examSubject = bySubject.get(taught.getSubject().getId());
            if (examSubject == null) {
                continue;
            }
            int expected = scope.expected(taught.getSection().getId());
            int done = Math.min(expected, entered.getOrDefault(examSubject.getId() + ":" + taught.getSection().getId(), 0));
            missing += expected - done;
            rows.add(new ExamDtos.ProgressRow(examSubject.getId(), taught.getSubject().getName(),
                    taught.getSection().getId(), sectionName(taught), taught.getTeacherId() == null ? null
                            : teacherNames.get(taught.getTeacherId()), expected, done));
        }
        rows.sort(Comparator.comparing(ExamDtos.ProgressRow::sectionName)
                .thenComparing(ExamDtos.ProgressRow::subjectName));
        return new ExamDtos.ProgressResponse(exam.getId(), exam.getStatus(), missing == 0, missing, rows);
    }

    static String sectionName(SectionSubject taught) {
        return taught.getSection().getGradeLevel().getName() + " - " + taught.getSection().getName();
    }

    private static String describe(Exam exam) {
        return "Exam \"" + exam.getName() + "\" (" + exam.getStatus() + ")";
    }
}
