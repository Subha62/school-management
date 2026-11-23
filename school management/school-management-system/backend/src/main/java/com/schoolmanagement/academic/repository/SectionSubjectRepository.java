package com.schoolmanagement.academic.repository;

import com.schoolmanagement.academic.entity.SectionSubject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SectionSubjectRepository
        extends JpaRepository<SectionSubject, UUID>, JpaSpecificationExecutor<SectionSubject> {

    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Optional<SectionSubject> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"academicYear", "section", "section.gradeLevel", "subject"})
    Page<SectionSubject> findAll(Specification<SectionSubject> spec, Pageable pageable);

    boolean existsBySectionIdAndSubjectId(UUID sectionId, UUID subjectId);

    Optional<SectionSubject> findBySectionIdAndSubjectId(UUID sectionId, UUID subjectId);

    boolean existsBySectionIdAndSubjectIdAndTeacherIdAndActiveTrue(UUID sectionId, UUID subjectId,
            UUID teacherId);

    @EntityGraph(attributePaths = {"section", "section.gradeLevel", "subject", "academicYear"})
    List<SectionSubject> findAllByTeacherIdAndActiveTrueAndSchoolId(UUID teacherId, UUID schoolId);

    boolean existsBySectionId(UUID sectionId);

    boolean existsBySectionIdAndTeacherIdAndActiveTrue(UUID sectionId, UUID teacherId);

    /** Active subject assignments of every class of a grade in a year (which subject is taught where, and by whom). */
    @EntityGraph(attributePaths = {"section", "section.gradeLevel", "subject"})
    @org.springframework.data.jpa.repository.Query("select a from SectionSubject a where a.academicYear.id = :yearId "
            + "and a.section.gradeLevel.id = :gradeLevelId and a.active = true")
    List<SectionSubject> findActiveForGrade(@org.springframework.data.repository.query.Param("yearId") UUID yearId,
            @org.springframework.data.repository.query.Param("gradeLevelId") UUID gradeLevelId);

    boolean existsBySubjectId(UUID subjectId);
}
