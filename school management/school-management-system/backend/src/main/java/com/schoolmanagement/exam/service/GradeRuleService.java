package com.schoolmanagement.exam.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.exam.entity.GradeRule;
import com.schoolmanagement.exam.repository.GradeRuleRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school's grade scale. Bands are inclusive percentage ranges that must not overlap. Gaps are allowed while the
 * scale is being built, but an exam cannot be locked or published until the scale covers 0 to 100 completely.
 * Published results keep the grades they were given, so editing the scale never changes them.
 */
@Service
public class GradeRuleService {

    private static final String ENTITY = "GradeRule";
    private static final BigDecimal STEP = new BigDecimal("0.01");

    private final GradeRuleRepository rules;
    private final AuditEventPublisher audit;

    public GradeRuleService(GradeRuleRepository rules, AuditEventPublisher audit) {
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<GradeRule> search(UUID schoolId, Pageable pageable) {
        return rules.findAll(Specification.<GradeRule>where((root, query, cb) -> cb.equal(root.get("schoolId"),
                schoolId)), pageable);
    }

    @Transactional(readOnly = true)
    public List<GradeRule> scale(UUID schoolId) {
        return rules.findAllBySchoolIdOrderByMinPercentAsc(schoolId);
    }

    @Transactional
    public GradeRule create(AuthenticatedUser actor, ExamDtos.GradeRuleRequest request) {
        rules.lockWrites("grade-rules:" + actor.schoolId());
        String grade = request.grade().trim();
        checkRange(request.minPercent(), request.maxPercent());
        if (rules.existsBySchoolIdAndGradeIgnoreCase(actor.schoolId(), grade)) {
            throw new ConflictException("The grade \"" + grade + "\" already exists.");
        }
        requireNoOverlap(actor.schoolId(), request, new UUID(0, 0));
        GradeRule rule = rules.saveAndFlush(new GradeRule(actor.schoolId(), grade, blankToNull(request.description()),
                request.minPercent(), request.maxPercent()));
        audit.record(actor, "GRADE_RULE_CREATED", ENTITY, rule.getId(), describe(rule));
        return rule;
    }

    @Transactional
    public GradeRule update(AuthenticatedUser actor, UUID id, ExamDtos.GradeRuleRequest request) {
        rules.lockWrites("grade-rules:" + actor.schoolId());
        GradeRule rule = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), rule.getVersion());
        String grade = request.grade().trim();
        checkRange(request.minPercent(), request.maxPercent());
        if (rules.existsBySchoolIdAndGradeIgnoreCaseAndIdNot(actor.schoolId(), grade, id)) {
            throw new ConflictException("The grade \"" + grade + "\" already exists.");
        }
        requireNoOverlap(actor.schoolId(), request, id);
        java.util.Map<String, Object> before = java.util.Map.of("grade", rule.getGrade(), "minPercent",
                rule.getMinPercent(), "maxPercent", rule.getMaxPercent());
        rule.update(grade, blankToNull(request.description()), request.minPercent(), request.maxPercent());
        rules.saveAndFlush(rule);
        audit.record(actor, "GRADE_RULE_UPDATED", ENTITY, id, describe(rule), before,
                java.util.Map.of("grade", rule.getGrade(), "minPercent", rule.getMinPercent(), "maxPercent",
                        rule.getMaxPercent()));
        return rule;
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        rules.lockWrites("grade-rules:" + actor.schoolId());
        GradeRule rule = get(actor.schoolId(), id);
        rules.delete(rule);
        audit.record(actor, "GRADE_RULE_DELETED", ENTITY, id, describe(rule));
    }

    @Transactional(readOnly = true)
    public GradeRule get(UUID schoolId, UUID id) {
        return rules.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Grade rule"));
    }

    /** Fails with a readable message unless every percentage from 0.00 to 100.00 has exactly one grade. */
    @Transactional(readOnly = true)
    public void requireCoverage(UUID schoolId) {
        List<GradeRule> scale = scale(schoolId);
        if (scale.isEmpty()) {
            throw new BusinessRuleException("Set up the grade scale before locking or publishing an exam.");
        }
        BigDecimal expected = BigDecimal.ZERO;
        for (GradeRule rule : scale) {
            if (rule.getMinPercent().compareTo(expected) != 0) {
                throw new BusinessRuleException("The grade scale has a gap: nothing covers "
                        + expected.toPlainString() + "% to " + rule.getMinPercent().subtract(STEP).toPlainString()
                        + "%.");
            }
            expected = rule.getMaxPercent().add(STEP);
        }
        BigDecimal last = scale.get(scale.size() - 1).getMaxPercent();
        if (last.compareTo(new BigDecimal("100")) != 0) {
            throw new BusinessRuleException("The grade scale has a gap: nothing covers " + expected.toPlainString()
                    + "% to 100%.");
        }
    }

    private void requireNoOverlap(UUID schoolId, ExamDtos.GradeRuleRequest request, UUID excludeId) {
        if (rules.overlaps(schoolId, request.minPercent(), request.maxPercent(), excludeId)) {
            throw new ConflictException("These percentages overlap another grade. Bands must not share a percentage.");
        }
    }

    private static void checkRange(BigDecimal min, BigDecimal max) {
        if (min.compareTo(max) > 0) {
            throw new FieldValidationException("maxPercent", "Maximum must not be less than the minimum");
        }
    }

    private static String describe(GradeRule rule) {
        return "Grade " + rule.getGrade() + " " + rule.getMinPercent().toPlainString() + "-"
                + rule.getMaxPercent().toPlainString() + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
