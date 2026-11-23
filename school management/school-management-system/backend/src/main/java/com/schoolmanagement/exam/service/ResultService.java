package com.schoolmanagement.exam.service;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.entity.Exam;
import com.schoolmanagement.exam.entity.ExamComment;
import com.schoolmanagement.exam.entity.ExamResult;
import com.schoolmanagement.exam.entity.ExamStatus;
import com.schoolmanagement.exam.entity.ExamSubject;
import com.schoolmanagement.exam.entity.GradeRule;
import com.schoolmanagement.exam.entity.StudentMark;
import com.schoolmanagement.exam.repository.ExamCommentRepository;
import com.schoolmanagement.exam.repository.ExamRepository;
import com.schoolmanagement.exam.repository.ExamResultRepository;
import com.schoolmanagement.exam.repository.StudentMarkRepository;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns entered marks into results. A locked exam is calculated on demand (a preview for administrators); when it is
 * published the results are frozen, and from then on administrators and students read the frozen copy.
 */
@Service
public class ResultService {

    private final ExamRepository exams;
    private final ExamScope scopes;
    private final StudentMarkRepository marks;
    private final ExamCommentRepository comments;
    private final ExamResultRepository stored;
    private final GradeRuleService gradeRules;
    private final SectionRepository sections;
    private final StudentProfileRepository students;
    private final SchoolRepository schools;

    public ResultService(ExamRepository exams, ExamScope scopes, StudentMarkRepository marks,
            ExamCommentRepository comments, ExamResultRepository stored, GradeRuleService gradeRules,
            SectionRepository sections, StudentProfileRepository students, SchoolRepository schools) {
        this.exams = exams;
        this.scopes = scopes;
        this.marks = marks;
        this.comments = comments;
        this.stored = stored;
        this.gradeRules = gradeRules;
        this.sections = sections;
        this.students = students;
        this.schools = schools;
    }

    private record Computed(StudentEnrollment enrollment, ResultCalculator.Outcome outcome, String comment) {
    }

    /** Freezes the results of every student of the exam and returns how many were stored. */
    @Transactional
    public int freeze(Exam exam) {
        List<Computed> computed = compute(exam, null, null);
        for (Computed c : computed) {
            StudentEnrollment e = c.enrollment();
            ResultCalculator.Outcome o = c.outcome();
            stored.save(new ExamResult(exam, e.getStudent(), e.getSection(), sectionName(e.getSection()),
                    e.getRollNumber(), o.totalMarks(), o.totalMax(), o.percentage(), o.grade(), o.result(),
                    c.comment(), o.subjects()));
        }
        stored.flush();
        return computed.size();
    }

    // ---- administrators ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ExamDtos.ResultsResponse adminResults(UUID schoolId, UUID examId, UUID sectionId) {
        Exam exam = readable(schoolId, examId);
        Section section = sections.findByIdAndSchoolId(sectionId, schoolId)
                .filter(s -> s.getAcademicYear().getId().equals(exam.getAcademicYear().getId())
                        && s.getGradeLevel().getId().equals(exam.getGradeLevel().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        boolean published = exam.getStatus() == ExamStatus.PUBLISHED;
        List<ExamDtos.StudentResult> list = published
                ? stored.findAllByExamAndSection(examId, section.getId()).stream().map(ResultService::fromStored)
                        .toList()
                : compute(exam, section.getId(), null).stream().map(ResultService::fromComputed).toList();
        return new ExamDtos.ResultsResponse(examId, exam.getName(), exam.getStatus(), !published, section.getId(),
                list);
    }

    @Transactional(readOnly = true)
    public ExamDtos.ReportCard adminReportCard(UUID schoolId, UUID examId, UUID studentId) {
        Exam exam = readable(schoolId, examId);
        ExamDtos.StudentResult result;
        if (exam.getStatus() == ExamStatus.PUBLISHED) {
            result = stored.findByExamIdAndStudentId(examId, studentId).map(ResultService::fromStored)
                    .orElseThrow(() -> new ResourceNotFoundException("Result"));
        } else {
            result = compute(exam, null, studentId).stream().findFirst().map(ResultService::fromComputed)
                    .orElseThrow(() -> new ResourceNotFoundException("Result"));
        }
        return card(exam, result, schoolId);
    }

    private Exam readable(UUID schoolId, UUID examId) {
        Exam exam = exams.findByIdAndSchoolId(examId, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam"));
        if (exam.getStatus() != ExamStatus.LOCKED && exam.getStatus() != ExamStatus.PUBLISHED) {
            throw new BusinessRuleException("Results are available once the exam is locked.");
        }
        return exam;
    }

    // ---- students ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ExamDtos.ResultSummary> studentResults(AuthenticatedUser actor, Pageable pageable) {
        StudentProfile student = profile(actor);
        Page<ExamResult> page = stored.findPublishedByStudent(student.getId(), pageable);
        return PageResponse.from(page, r -> new ExamDtos.ResultSummary(r.getExam().getId(), r.getExam().getName(),
                r.getExam().getAcademicYear().getName(), r.getExam().getGradeLevel().getName(),
                r.getExam().getPublishedAt(), r.getPercentage(), r.getGrade(), r.getResult()));
    }

    @Transactional(readOnly = true)
    public ExamDtos.ReportCard studentReportCard(AuthenticatedUser actor, UUID examId) {
        StudentProfile student = profile(actor);
        ExamResult result = stored.findByExamIdAndStudentId(examId, student.getId())
                .filter(r -> r.getExam().getStatus() == ExamStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Result"));
        return card(result.getExam(), fromStored(result), actor.schoolId());
    }

    private StudentProfile profile(AuthenticatedUser actor) {
        return students.findByUserId(actor.id()).filter(p -> p.getSchoolId().equals(actor.schoolId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student"));
    }

    // ---- calculation ------------------------------------------------------------------------------------

    /** Results of the students of one class and/or one student (or everyone when both filters are null). */
    private List<Computed> compute(Exam exam, UUID sectionFilter, UUID studentFilter) {
        ExamScope.Scope scope = scopes.of(exam);
        List<GradeRule> scale = gradeRules.scale(exam.getSchoolId());
        Map<String, StudentMark> entered = marks.findAllByExamId(exam.getId()).stream().collect(
                Collectors.toMap(m -> m.getExamSubject().getId() + ":" + m.getStudent().getId(),
                        Function.identity()));
        Map<UUID, ExamComment> written = comments.findAllByExamId(exam.getId()).stream()
                .collect(Collectors.toMap(c -> c.getStudent().getId(), Function.identity()));
        List<ExamSubject> examSubjects = exam.getSubjects().stream()
                .sorted(java.util.Comparator.comparing(s -> s.getSubject().getName())).toList();

        List<Computed> out = new ArrayList<>();
        for (Map.Entry<UUID, List<StudentEnrollment>> roster : scope.rosters().entrySet()) {
            if (sectionFilter != null && !sectionFilter.equals(roster.getKey())) {
                continue;
            }
            List<SectionSubject> taught = scope.taught().stream()
                    .filter(a -> a.getSection().getId().equals(roster.getKey())).toList();
            Map<UUID, Boolean> offered = new HashMap<>();
            taught.forEach(a -> offered.put(a.getSubject().getId(), true));
            for (StudentEnrollment enrollment : roster.getValue()) {
                UUID studentId = enrollment.getStudent().getId();
                if (studentFilter != null && !studentFilter.equals(studentId)) {
                    continue;
                }
                List<ResultCalculator.Line> lines = new ArrayList<>();
                for (ExamSubject es : examSubjects) {
                    if (!offered.containsKey(es.getSubject().getId())) {
                        continue;
                    }
                    StudentMark mark = entered.get(es.getId() + ":" + studentId);
                    if (mark == null) {
                        throw new BusinessRuleException("Marks are incomplete: " + displayName(enrollment.getStudent())
                                + " has no " + es.getSubject().getName() + " entry.");
                    }
                    lines.add(new ResultCalculator.Line(es.getSubject().getId(), es.getSubject().getCode(),
                            es.getSubject().getName(), es.getMaxMarks(), es.getPassMarks(), mark.getMarks(),
                            mark.isAbsent(), mark.getRemarks()));
                }
                if (lines.isEmpty()) {
                    continue;
                }
                ExamComment comment = written.get(studentId);
                out.add(new Computed(enrollment, ResultCalculator.calculate(lines, scale),
                        comment == null ? null : comment.getComment()));
            }
        }
        return out;
    }

    private static ExamDtos.StudentResult fromComputed(Computed c) {
        StudentEnrollment e = c.enrollment();
        ResultCalculator.Outcome o = c.outcome();
        return new ExamDtos.StudentResult(e.getStudent().getId(), e.getStudent().getAdmissionNumber(),
                displayName(e.getStudent()), e.getSection().getId(), sectionName(e.getSection()), e.getRollNumber(),
                o.subjects(), o.totalMarks(), o.totalMax(), o.percentage(), o.grade(), o.result(), c.comment());
    }

    private static ExamDtos.StudentResult fromStored(ExamResult r) {
        return new ExamDtos.StudentResult(r.getStudent().getId(), r.getStudent().getAdmissionNumber(),
                displayName(r.getStudent()), r.getSection().getId(), r.getSectionName(), r.getRollNumber(),
                r.getSubjects(), r.getTotalMarks(), r.getTotalMax(), r.getPercentage(), r.getGrade(), r.getResult(),
                r.getComment());
    }

    private ExamDtos.ReportCard card(Exam exam, ExamDtos.StudentResult result, UUID schoolId) {
        String school = schools.findById(schoolId).map(s -> s.getName()).orElse("");
        return new ExamDtos.ReportCard(exam.getId(), exam.getName(), school, exam.getAcademicYear().getName(),
                exam.getGradeLevel().getName(), exam.getStartDate(), exam.getEndDate(), exam.getPublishedAt(),
                result);
    }

    static String sectionName(Section section) {
        return section.getGradeLevel().getName() + " - " + section.getName();
    }

    static String displayName(StudentProfile student) {
        return (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim();
    }
}
