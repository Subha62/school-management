package com.schoolmanagement.fee.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.config.FeeProperties;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.entity.StudentFee;
import com.schoolmanagement.fee.repository.PaymentRepository;
import com.schoolmanagement.fee.repository.StudentFeeRepository;
import com.schoolmanagement.school.service.SchoolTime;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What students owe: the administrators' view of every bill, waivers, and the student's own view of their bills. A
 * student can only ever reach their own fees; teachers have no access to financial data at all.
 */
@Service
public class StudentFeeService {

    private static final String ENTITY = "StudentFee";

    private final StudentFeeRepository fees;
    private final PaymentRepository payments;
    private final StudentProfileRepository students;
    private final FeeMapper mapper;
    private final SchoolTime schoolTime;
    private final FeeProperties properties;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public StudentFeeService(StudentFeeRepository fees, PaymentRepository payments, StudentProfileRepository students,
            FeeMapper mapper, SchoolTime schoolTime, FeeProperties properties, AuditEventPublisher audit,
            Clock clock) {
        this.fees = fees;
        this.payments = payments;
        this.students = students;
        this.mapper = mapper;
        this.schoolTime = schoolTime;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    /** The common filter of the lists and the reports. */
    static Specification<StudentFee> inScope(UUID schoolId, UUID yearId, UUID gradeId) {
        Specification<StudentFee> spec = SearchSpecs.equalTo("schoolId", schoolId);
        if (yearId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("academicYear").get("id"), yearId));
        }
        if (gradeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("structure").get("gradeLevel").get("id"), gradeId));
        }
        return spec;
    }

    // ---- administrators ---------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<FeeDtos.StudentFeeResponse> search(UUID schoolId, UUID yearId, UUID gradeId, UUID categoryId,
            UUID studentId, FeeStatus status, String search, Pageable pageable) {
        LocalDate today = schoolTime.today(schoolId);
        Specification<StudentFee> spec = inScope(schoolId, yearId, gradeId)
                .and(FeeStatusRules.matching(status, today, properties.dueSoonDays()));
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("structure").get("category").get("id"), categoryId));
        }
        if (studentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("student").get("id"), studentId));
        }
        if (search != null && !search.isBlank()) {
            String pattern = SearchSpecs.likePattern(search);
            spec = spec.and((root, query, cb) -> {
                var student = root.join("student");
                var user = student.join("user");
                return cb.or(cb.like(cb.lower(student.get("admissionNumber")), pattern, '\\'),
                        cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))),
                                pattern, '\\'));
            });
        }
        Page<StudentFee> page = fees.findAll(spec, pageable);
        List<FeeDtos.StudentFeeResponse> views = mapper.fees(page.getContent(), today);
        return new PageResponse<>(views, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public FeeDtos.StudentFeeDetail get(UUID schoolId, UUID id) {
        return detail(fees.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Fee")),
                schoolTime.today(schoolId));
    }

    @Transactional
    public FeeDtos.StudentFeeDetail waive(AuthenticatedUser actor, UUID id, FeeDtos.ReasonRequest request) {
        StudentFee fee = lock(actor, id);
        fee.waive(request.reason().trim(), actor.id(), clock.instant());
        fees.saveAndFlush(fee);
        audit.record(actor, "FEE_WAIVED", ENTITY, id, describe(fee) + ": " + fee.getWaiveReason());
        return detail(fee, schoolTime.today(actor.schoolId()));
    }

    @Transactional
    public FeeDtos.StudentFeeDetail unwaive(AuthenticatedUser actor, UUID id, FeeDtos.ReasonRequest request) {
        StudentFee fee = lock(actor, id);
        fee.unwaive();
        fees.saveAndFlush(fee);
        audit.record(actor, "FEE_WAIVER_REMOVED", ENTITY, id, describe(fee) + ": " + request.reason().trim());
        return detail(fee, schoolTime.today(actor.schoolId()));
    }

    private StudentFee lock(AuthenticatedUser actor, UUID id) {
        StudentFee fee = fees.lockForUpdate(id, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Fee"));
        return fees.findByIdAndSchoolId(fee.getId(), actor.schoolId()).orElseThrow();
    }

    private FeeDtos.StudentFeeDetail detail(StudentFee fee, LocalDate today) {
        return new FeeDtos.StudentFeeDetail(mapper.fee(fee, mapper.className(fee.getStudent().getId()), today),
                mapper.payments(payments.findAllByStudentFeeIdOrderByCreatedAtDesc(fee.getId())));
    }

    // ---- students ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<FeeDtos.StudentFeeResponse> mine(AuthenticatedUser actor, FeeStatus status,
            Pageable pageable) {
        StudentProfile student = profile(actor);
        LocalDate today = schoolTime.today(actor.schoolId());
        Specification<StudentFee> spec = inScope(actor.schoolId(), null, null)
                .and((root, query, cb) -> cb.equal(root.get("student").get("id"), student.getId()))
                .and(FeeStatusRules.matching(status, today, properties.dueSoonDays()));
        Page<StudentFee> page = fees.findAll(spec, pageable);
        List<FeeDtos.StudentFeeResponse> views = mapper.fees(page.getContent(), today);
        return new PageResponse<>(views, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public FeeDtos.StudentFeeDetail mineDetail(AuthenticatedUser actor, UUID id) {
        StudentProfile student = profile(actor);
        StudentFee fee = fees.findByIdAndSchoolId(id, actor.schoolId())
                .filter(f -> f.getStudent().getId().equals(student.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Fee"));
        return detail(fee, schoolTime.today(actor.schoolId()));
    }

    StudentProfile profile(AuthenticatedUser actor) {
        return students.findByUserId(actor.id()).filter(p -> p.getSchoolId().equals(actor.schoolId()))
                .orElseThrow(() -> new ResourceNotFoundException("Student"));
    }

    private static String describe(StudentFee fee) {
        return fee.getCategoryName() + " " + fee.getAmount().toPlainString() + " for "
                + fee.getStudent().getAdmissionNumber();
    }
}
