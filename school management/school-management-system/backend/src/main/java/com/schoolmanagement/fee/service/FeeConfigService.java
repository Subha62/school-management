package com.schoolmanagement.fee.service;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.entity.GradeLevel;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.GradeLevelRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldError;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.FeeCategory;
import com.schoolmanagement.fee.entity.FeeStructure;
import com.schoolmanagement.fee.entity.StudentFee;
import com.schoolmanagement.fee.repository.FeeCategoryRepository;
import com.schoolmanagement.fee.repository.FeeStructureRepository;
import com.schoolmanagement.fee.repository.StudentFeeRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fee set-up: categories, the structures that say what a grade pays and when, and generating each student's bill
 * from a structure. Bills are copies, so changing a structure never rewrites what students were charged.
 */
@Service
public class FeeConfigService {

    private final FeeCategoryRepository categories;
    private final FeeStructureRepository structures;
    private final StudentFeeRepository studentFees;
    private final AcademicYearRepository years;
    private final GradeLevelRepository grades;
    private final StudentEnrollmentRepository enrollments;
    private final AuditEventPublisher audit;

    public FeeConfigService(FeeCategoryRepository categories, FeeStructureRepository structures,
            StudentFeeRepository studentFees, AcademicYearRepository years, GradeLevelRepository grades,
            StudentEnrollmentRepository enrollments, AuditEventPublisher audit) {
        this.categories = categories;
        this.structures = structures;
        this.studentFees = studentFees;
        this.years = years;
        this.grades = grades;
        this.enrollments = enrollments;
        this.audit = audit;
    }

    // ---- categories -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<FeeCategory> searchCategories(UUID schoolId, Boolean active, String search, Pageable pageable) {
        return categories.findAll(Specification.<FeeCategory>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("active", active)).and(SearchSpecs.containsIgnoreCase("name", search)),
                pageable);
    }

    @Transactional
    public FeeCategory createCategory(AuthenticatedUser actor, FeeDtos.CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsBySchoolIdAndNameIgnoreCase(actor.schoolId(), name)) {
            throw new ConflictException("A fee category named \"" + name + "\" already exists.");
        }
        FeeCategory category = categories.saveAndFlush(new FeeCategory(actor.schoolId(), name,
                blankToNull(request.description()), request.active()));
        audit.record(actor, "FEE_CATEGORY_CREATED", "FeeCategory", category.getId(), "Created fee category " + name);
        return category;
    }

    @Transactional
    public FeeCategory updateCategory(AuthenticatedUser actor, UUID id, FeeDtos.CategoryRequest request) {
        FeeCategory category = category(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), category.getVersion());
        String name = request.name().trim();
        if (categories.existsBySchoolIdAndNameIgnoreCaseAndIdNot(actor.schoolId(), name, id)) {
            throw new ConflictException("A fee category named \"" + name + "\" already exists.");
        }
        java.util.Map<String, Object> before = java.util.Map.of("name", category.getName(), "active",
                category.isActive());
        category.update(name, blankToNull(request.description()), request.active());
        categories.saveAndFlush(category);
        audit.record(actor, "FEE_CATEGORY_UPDATED", "FeeCategory", id, "Updated fee category " + name, before,
                java.util.Map.of("name", name, "active", request.active()));
        return category;
    }

    @Transactional
    public void deleteCategory(AuthenticatedUser actor, UUID id) {
        FeeCategory category = category(actor.schoolId(), id);
        if (structures.existsByCategoryId(id)) {
            throw new ConflictException("This category is used by fee structures. Mark it inactive instead.");
        }
        categories.delete(category);
        audit.record(actor, "FEE_CATEGORY_DELETED", "FeeCategory", id, "Deleted fee category " + category.getName());
    }

    private FeeCategory category(UUID schoolId, UUID id) {
        return categories.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee category"));
    }

    // ---- structures -------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<FeeDtos.StructureResponse> searchStructures(UUID schoolId, UUID yearId, UUID gradeId,
            UUID categoryId, Boolean active, Pageable pageable) {
        Specification<FeeStructure> spec = Specification
                .<FeeStructure>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("active", active));
        if (yearId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), yearId));
        }
        if (gradeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("gradeLevel").get("id"), gradeId));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        Page<FeeStructure> page = structures.findAll(spec, pageable);
        Map<UUID, Long> billed = billedCounts(page.getContent());
        return PageResponse.from(page, s -> view(s, billed.getOrDefault(s.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public FeeDtos.StructureResponse getStructure(UUID schoolId, UUID id) {
        FeeStructure structure = structure(schoolId, id);
        return view(structure, studentFees.countByStructureId(id));
    }

    @Transactional
    public FeeDtos.StructureResponse createStructure(AuthenticatedUser actor, FeeDtos.StructureRequest request) {
        List<FieldError> missing = new ArrayList<>();
        if (request.academicYearId() == null) {
            missing.add(new FieldError("academicYearId", "Academic year is required"));
        }
        if (request.gradeLevelId() == null) {
            missing.add(new FieldError("gradeLevelId", "Grade is required"));
        }
        if (request.feeCategoryId() == null) {
            missing.add(new FieldError("feeCategoryId", "Category is required"));
        }
        if (!missing.isEmpty()) {
            throw new FieldValidationException(missing);
        }
        AcademicYear year = years.findByIdAndSchoolId(request.academicYearId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic year"));
        if (year.isClosed()) {
            throw new BusinessRuleException("The academic year is closed, so its fees can no longer change.");
        }
        GradeLevel grade = grades.findByIdAndSchoolId(request.gradeLevelId(), actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Grade"));
        FeeCategory category = category(actor.schoolId(), request.feeCategoryId());
        if (!category.isActive()) {
            throw new BusinessRuleException("The fee category is inactive.");
        }
        if (structures.existsByAcademicYearIdAndGradeLevelIdAndCategoryIdAndDueDate(year.getId(), grade.getId(),
                category.getId(), request.dueDate())) {
            throw new ConflictException("This grade is already charged " + category.getName() + " with this due date.");
        }
        FeeStructure structure = structures.saveAndFlush(new FeeStructure(actor.schoolId(), year, grade, category,
                blankToNull(request.description()), request.amount(), request.dueDate(), request.active()));
        audit.record(actor, "FEE_STRUCTURE_CREATED", "FeeStructure", structure.getId(), describe(structure));
        return view(structure, 0);
    }

    @Transactional
    public FeeDtos.StructureResponse updateStructure(AuthenticatedUser actor, UUID id,
            FeeDtos.StructureRequest request) {
        FeeStructure structure = structures.lockForUpdate(id, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Fee structure"));
        OptimisticVersion.check(request.version(), structure.getVersion());
        long billed = studentFees.countByStructureId(id);
        boolean termsChange = request.amount().compareTo(structure.getAmount()) != 0
                || !request.dueDate().equals(structure.getDueDate());
        if (billed > 0 && termsChange) {
            throw new BusinessRuleException("Fees have already been generated from this structure, so its amount and "
                    + "due date can no longer change. Add a new structure instead.");
        }
        if (structure.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("The academic year is closed, so its fees can no longer change.");
        }
        java.util.Map<String, Object> before = java.util.Map.of("description", String.valueOf(structure.getDescription()),
                "amount", structure.getAmount(), "dueDate", structure.getDueDate(), "active", structure.isActive());
        structure.update(blankToNull(request.description()), request.amount(), request.dueDate(), request.active());
        structures.saveAndFlush(structure);
        audit.record(actor, "FEE_STRUCTURE_UPDATED", "FeeStructure", id, describe(structure), before,
                java.util.Map.of("description", String.valueOf(structure.getDescription()), "amount",
                        structure.getAmount(), "dueDate", structure.getDueDate(), "active", structure.isActive()));
        return view(structure, billed);
    }

    @Transactional
    public void deleteStructure(AuthenticatedUser actor, UUID id) {
        FeeStructure structure = structure(actor.schoolId(), id);
        if (studentFees.existsByStructureId(id)) {
            throw new ConflictException("Fees were generated from this structure, so it cannot be deleted. Mark it "
                    + "inactive instead.");
        }
        structures.delete(structure);
        audit.record(actor, "FEE_STRUCTURE_DELETED", "FeeStructure", id, describe(structure));
    }

    /**
     * Bills every student currently enrolled in the structure's grade who has not been billed yet. Running it again
     * only adds students enrolled since, so it is safe to repeat.
     */
    @Transactional
    public FeeDtos.GenerateResult generate(AuthenticatedUser actor, UUID id) {
        FeeStructure structure = structures.lockForUpdate(id, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Fee structure"));
        if (!structure.isActive()) {
            throw new BusinessRuleException("This fee structure is inactive.");
        }
        if (structure.getAcademicYear().isClosed() || structure.getAcademicYear().getStatus() != AcademicYearStatus.ACTIVE) {
            throw new BusinessRuleException("Fees can only be generated in the active academic year.");
        }
        Set<UUID> billed = new HashSet<>(studentFees.findBilledStudentIds(id));
        int created = 0;
        int skipped = 0;
        for (StudentEnrollment enrollment : enrollments.findActiveInYearAndGrade(structure.getAcademicYear().getId(),
                structure.getGradeLevel().getId())) {
            if (billed.contains(enrollment.getStudent().getId())) {
                skipped++;
            } else {
                studentFees.save(new StudentFee(enrollment.getStudent(), structure));
                created++;
            }
        }
        studentFees.flush();
        audit.record(actor, "FEES_GENERATED", "FeeStructure", id, describe(structure) + ": billed " + created
                + " student(s), " + skipped + " already billed");
        return new FeeDtos.GenerateResult(created, skipped);
    }

    private FeeStructure structure(UUID schoolId, UUID id) {
        return structures.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Fee structure"));
    }

    private Map<UUID, Long> billedCounts(List<FeeStructure> list) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : studentFees.countByStructures(list.stream().map(FeeStructure::getId).toList())) {
                counts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    private static FeeDtos.StructureResponse view(FeeStructure s, long billed) {
        return new FeeDtos.StructureResponse(s.getId(), s.getAcademicYear().getId(), s.getAcademicYear().getName(),
                s.getGradeLevel().getId(), s.getGradeLevel().getName(), s.getCategory().getId(),
                s.getCategory().getName(), s.getDescription(), s.getAmount(), s.getDueDate(), s.isActive(), billed,
                s.getVersion());
    }

    private static String describe(FeeStructure s) {
        return s.getCategory().getName() + " " + s.getAmount().toPlainString() + " for " + s.getGradeLevel().getName()
                + " due " + s.getDueDate();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
