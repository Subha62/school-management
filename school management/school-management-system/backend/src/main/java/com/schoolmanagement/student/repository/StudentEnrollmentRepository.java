package com.schoolmanagement.student.repository;

import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentEnrollment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentEnrollmentRepository
        extends JpaRepository<StudentEnrollment, UUID>, JpaSpecificationExecutor<StudentEnrollment> {

    String GRAPH_STUDENT = "student";

    @EntityGraph(attributePaths = {"student", "student.user", "academicYear", "gradeLevel", "section"})
    Optional<StudentEnrollment> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"student", "student.user", "academicYear", "gradeLevel", "section"})
    Page<StudentEnrollment> findAll(Specification<StudentEnrollment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "student.user", "academicYear", "gradeLevel", "section"})
    List<StudentEnrollment> findAllByStudentIdOrderByEnrollmentDateDescCreatedAtDesc(UUID studentId);

    /** Current placements of many students at once, newest academic year first (avoids one query per row). */
    @EntityGraph(attributePaths = {"academicYear", "gradeLevel", "section"})
    @Query("select e from StudentEnrollment e where e.student.id in :studentIds "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE "
            + "order by e.academicYear.startDate desc")
    List<StudentEnrollment> findActiveByStudentIds(@Param("studentIds") Collection<UUID> studentIds);

    @EntityGraph(attributePaths = {"student", "student.user", "academicYear", "gradeLevel", "section"})
    @Query("select e from StudentEnrollment e where e.student.id in :studentIds "
            + "and e.academicYear.id = :academicYearId "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE")
    List<StudentEnrollment> findActiveInYear(@Param("studentIds") Collection<UUID> studentIds,
            @Param("academicYearId") UUID academicYearId);

    boolean existsByStudentIdAndAcademicYearIdAndStatus(UUID studentId, UUID academicYearId,
            EnrollmentStatus status);

    boolean existsBySectionId(UUID sectionId);

    /** The students currently in a section, in roll-number order (students without one come last). */
    @EntityGraph(attributePaths = {"student", "student.user"})
    @Query("select e from StudentEnrollment e where e.section.id = :sectionId "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE "
            + "order by case when e.rollNumber is null then 1 else 0 end, e.rollNumber, e.student.user.lastName, "
            + "e.student.user.firstName")
    List<StudentEnrollment> findActiveBySectionId(@Param("sectionId") UUID sectionId);

    boolean existsByStudentId(UUID studentId);

    long countBySectionIdAndStatus(UUID sectionId, EnrollmentStatus status);

    @Query("select coalesce(max(e.rollNumber), 0) from StudentEnrollment e where e.section.id = :sectionId "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE")
    int maxActiveRollNumber(@Param("sectionId") UUID sectionId);

    boolean existsBySectionIdAndRollNumberAndStatus(UUID sectionId, Integer rollNumber, EnrollmentStatus status);

    @EntityGraph(attributePaths = {"student", "student.user", "section"})
    @Query("select e from StudentEnrollment e where e.academicYear.id = :yearId and e.gradeLevel.id = :gradeLevelId "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE "
            + "order by e.section.name, case when e.rollNumber is null then 1 else 0 end, e.rollNumber, "
            + "e.student.user.lastName, e.student.user.firstName")
    List<StudentEnrollment> findActiveInYearAndGrade(@Param("yearId") UUID yearId,
            @Param("gradeLevelId") UUID gradeLevelId);

    @Query("select e from StudentEnrollment e where e.student.id = :studentId "
            + "and e.status = com.schoolmanagement.student.entity.EnrollmentStatus.ACTIVE")
    List<StudentEnrollment> findAllActiveByStudentId(@Param("studentId") UUID studentId);
}
