package com.schoolmanagement.exam.repository;

import com.schoolmanagement.exam.entity.ExamResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {

    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("select r from ExamResult r where r.exam.id = :examId and r.section.id = :sectionId "
            + "order by case when r.rollNumber is null then 1 else 0 end, r.rollNumber, r.student.user.lastName, "
            + "r.student.user.firstName")
    List<ExamResult> findAllByExamAndSection(@Param("examId") UUID examId, @Param("sectionId") UUID sectionId);

    @EntityGraph(attributePaths = {"student", "student.user", "exam", "exam.academicYear", "exam.gradeLevel"})
    Optional<ExamResult> findByExamIdAndStudentId(UUID examId, UUID studentId);

    /** A student's results of exams that are published, newest first. */
    @EntityGraph(attributePaths = {"exam", "exam.academicYear", "exam.gradeLevel"})
    @Query(value = "select r from ExamResult r where r.student.id = :studentId "
            + "and r.exam.status = com.schoolmanagement.exam.entity.ExamStatus.PUBLISHED",
            countQuery = "select count(r) from ExamResult r where r.student.id = :studentId "
                    + "and r.exam.status = com.schoolmanagement.exam.entity.ExamStatus.PUBLISHED")
    Page<ExamResult> findPublishedByStudent(@Param("studentId") UUID studentId, Pageable pageable);
}
