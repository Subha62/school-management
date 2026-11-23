package com.schoolmanagement.exam.service;

import com.schoolmanagement.academic.entity.SectionSubject;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.exam.entity.Exam;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Who has to be marked in what: the students currently enrolled in each class of the exam's grade and year, and
 * which subjects each class is taught (and by whom). A subject that a class does not take is simply not part of that
 * class's results.
 */
@Component
class ExamScope {

    /** {@code rosters} is keyed by class (section) id, in class-name order. */
    record Scope(Map<UUID, List<StudentEnrollment>> rosters, List<SectionSubject> taught) {

        boolean isTaught(UUID sectionId, UUID subjectId) {
            return taught.stream().anyMatch(a -> a.getSection().getId().equals(sectionId)
                    && a.getSubject().getId().equals(subjectId));
        }

        int expected(UUID sectionId) {
            return rosters.getOrDefault(sectionId, List.of()).size();
        }
    }

    private final StudentEnrollmentRepository enrollments;
    private final SectionSubjectRepository sectionSubjects;

    ExamScope(StudentEnrollmentRepository enrollments, SectionSubjectRepository sectionSubjects) {
        this.enrollments = enrollments;
        this.sectionSubjects = sectionSubjects;
    }

    Scope of(Exam exam) {
        UUID yearId = exam.getAcademicYear().getId();
        UUID gradeId = exam.getGradeLevel().getId();
        Map<UUID, List<StudentEnrollment>> rosters = enrollments.findActiveInYearAndGrade(yearId, gradeId).stream()
                .collect(Collectors.groupingBy(e -> e.getSection().getId(), LinkedHashMap::new,
                        Collectors.toList()));
        return new Scope(rosters, sectionSubjects.findActiveForGrade(yearId, gradeId));
    }
}
