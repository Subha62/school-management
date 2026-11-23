package com.schoolmanagement.academic.service;

import com.schoolmanagement.academic.dto.GradeLevelDtos;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.academic.repository.GradeLevelRepository;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.exam.repository.ExamRepository;
import com.schoolmanagement.fee.repository.FeeStructureRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeLevelService {

    private static final String ENTITY = "GradeLevel";

    private final GradeLevelRepository grades;
    private final SectionRepository sections;
    private final ExamRepository examinations;
    private final FeeStructureRepository feeStructures;
    private final AuditEventPublisher audit;

    public GradeLevelService(GradeLevelRepository grades, SectionRepository sections, ExamRepository examinations,
            FeeStructureRepository feeStructures, AuditEventPublisher audit) {
        this.grades = grades;
        this.sections = sections;
        this.examinations = examinations;
        this.feeStructures = feeStructures;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<GradeLevel> search(UUID schoolId, Boolean active, String search, Pageable pageable) {
        Specification<GradeLevel> spec = Specification.<GradeLevel>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("active", active))
                .and(SearchSpecs.containsIgnoreCase("name", search));
        return grades.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public GradeLevel get(UUID schoolId, UUID id) {
        return grades.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Grade"));
    }

    @Transactional
    public GradeLevel create(AuthenticatedUser actor, GradeLevelDtos.Request request) {
        String name = request.name().trim();
        if (grades.existsBySchoolIdAndNameIgnoreCase(actor.schoolId(), name)) {
            throw new ConflictException("A grade named \"" + name + "\" already exists.");
        }
        GradeLevel grade = grades.saveAndFlush(
                new GradeLevel(actor.schoolId(), name, request.displayOrder(), request.active()));
        audit.record(actor, "GRADE_LEVEL_CREATED", ENTITY, grade.getId(), "Created grade " + name);
        return grade;
    }

    @Transactional
    public GradeLevel update(AuthenticatedUser actor, UUID id, GradeLevelDtos.Request request) {
        GradeLevel grade = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), grade.getVersion());
        String name = request.name().trim();
        if (grades.existsBySchoolIdAndNameIgnoreCaseAndIdNot(actor.schoolId(), name, id)) {
            throw new ConflictException("A grade named \"" + name + "\" already exists.");
        }
        grade.update(name, request.displayOrder(), request.active());
        grades.saveAndFlush(grade);
        audit.record(actor, "GRADE_LEVEL_UPDATED", ENTITY, id, "Updated grade " + name);
        return grade;
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        GradeLevel grade = get(actor.schoolId(), id);
        if (sections.existsByGradeLevelId(id) || examinations.existsByGradeLevelId(id)
                || feeStructures.existsByGradeLevelId(id)) {
            throw new ConflictException("This grade is used by sections, exams or fees. Mark it inactive instead of deleting it.");
        }
        grades.delete(grade);
        audit.record(actor, "GRADE_LEVEL_DELETED", ENTITY, id, "Deleted grade " + grade.getName());
    }
}
