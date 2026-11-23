package com.schoolmanagement.exam.repository;

import com.schoolmanagement.exam.entity.StudentMark;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentMarkRepository extends JpaRepository<StudentMark, UUID> {

    @EntityGraph(attributePaths = {"student"})
    List<StudentMark> findAllByExamSubjectIdAndStudentIdIn(UUID examSubjectId, Collection<UUID> studentIds);

    @EntityGraph(attributePaths = {"student", "examSubject"})
    @Query("select m from StudentMark m where m.examSubject.exam.id = :examId")
    List<StudentMark> findAllByExamId(@Param("examId") UUID examId);

    /**
     * How many students of each class currently have something entered for each exam subject. The class comes
     * from the student's active enrollment, so a student who moved class is counted where they are now.
     */
    @Query("select m.examSubject.id, e.section.id, count(m) from StudentMark m, StudentEnrollment e "
            + "where m.examSubject.exam.id = :examId and e.student.id = m.student.id "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE "
            + "and e.academicYear.id = :yearId and e.gradeLevel.id = :gradeLevelId "
            + "group by m.examSubject.id, e.section.id")
    List<Object[]> countEntered(@Param("examId") UUID examId, @Param("yearId") UUID yearId,
            @Param("gradeLevelId") UUID gradeLevelId);
}
