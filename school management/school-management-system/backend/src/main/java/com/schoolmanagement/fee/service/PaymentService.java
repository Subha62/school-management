package com.schoolmanagement.fee.service;

import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.Payment;
import com.schoolmanagement.fee.entity.PaymentMethod;
import com.schoolmanagement.fee.entity.PaymentReversal;
import com.schoolmanagement.fee.entity.StudentFee;
import com.schoolmanagement.fee.repository.PaymentRepository;
import com.schoolmanagement.fee.repository.PaymentReversalRepository;
import com.schoolmanagement.fee.repository.StudentFeeRepository;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.school.service.SchoolTime;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recording money. A payment must be positive, may not exceed the balance of its fee, and gets a unique receipt
 * number. Payments are never edited or deleted: a mistake is reversed, which keeps the original row, records who
 * reversed it and why, and gives the amount back to the fee's balance. Everything happens while the fee row is
 * locked, so concurrent cashiers cannot overpay a fee or reverse the same payment twice.
 */
@Service
public class PaymentService {

    private static final String ENTITY = "Payment";

    private final PaymentRepository payments;
    private final PaymentReversalRepository reversals;
    private final StudentFeeRepository fees;
    private final ReceiptNumbers receipts;
    private final FeeMapper mapper;
    private final SchoolTime schoolTime;
    private final SchoolRepository schools;
    private final TeacherDirectory directory;
    private final StudentFeeService studentFees;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentReversalRepository reversals, StudentFeeRepository fees,
            ReceiptNumbers receipts, FeeMapper mapper, SchoolTime schoolTime, SchoolRepository schools,
            TeacherDirectory directory, StudentFeeService studentFees, AuditEventPublisher audit, Clock clock) {
        this.payments = payments;
        this.reversals = reversals;
        this.fees = fees;
        this.receipts = receipts;
        this.mapper = mapper;
        this.schoolTime = schoolTime;
        this.schools = schools;
        this.directory = directory;
        this.studentFees = studentFees;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public FeeDtos.PaymentResponse record(AuthenticatedUser actor, UUID feeId, FeeDtos.PaymentRequest request) {
        StudentFee fee = fees.lockForUpdate(feeId, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Fee"));
        LocalDate today = schoolTime.today(actor.schoolId());
        if (request.paidOn().isAfter(today)) {
            throw new FieldValidationException("paidOn", "The payment date cannot be in the future");
        }
        fee.applyPayment(request.amount());
        String reference = request.reference() == null || request.reference().isBlank() ? null
                : request.reference().trim();
        Payment payment = payments.saveAndFlush(new Payment(fee, receipts.next(actor.schoolId(), today),
                request.amount(), request.method(), reference, request.paidOn(), actor.id()));
        fees.saveAndFlush(fee);
        audit.record(actor, "PAYMENT_RECORDED", ENTITY, payment.getId(), "Receipt " + payment.getReceiptNumber() + ": "
                + payment.getAmount().toPlainString() + " by " + payment.getMethod() + " for "
                + fee.getStudent().getAdmissionNumber() + " (" + fee.getCategoryName() + ")");
        return one(payment);
    }

    @Transactional
    public FeeDtos.PaymentResponse reverse(AuthenticatedUser actor, UUID paymentId, FeeDtos.ReasonRequest request) {
        Payment payment = payments.findByIdAndSchoolId(paymentId, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment"));
        StudentFee fee = fees.lockForUpdate(payment.getStudentFee().getId(), actor.schoolId()).orElseThrow();
        if (reversals.existsByPaymentId(paymentId)) {
            throw new ConflictException("This payment has already been reversed.");
        }
        reversals.saveAndFlush(new PaymentReversal(payment, request.reason().trim(), actor.id(), clock.instant()));
        fee.takeBack(payment.getAmount());
        fees.saveAndFlush(fee);
        audit.record(actor, "PAYMENT_REVERSED", ENTITY, paymentId, "Receipt " + payment.getReceiptNumber() + " ("
                + payment.getAmount().toPlainString() + ") reversed: " + request.reason().trim());
        return one(payment);
    }

    // ---- reading ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<FeeDtos.PaymentResponse> search(UUID schoolId, LocalDate from, LocalDate to,
            PaymentMethod method, UUID studentId, Boolean reversed, String search, Pageable pageable) {
        Specification<Payment> spec = Specification.<Payment>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("method", method));
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDate>get("paidOn"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDate>get("paidOn"), to));
        }
        if (studentId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("student").get("id"), studentId));
        }
        if (reversed != null) {
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<PaymentReversal> r = sub.from(PaymentReversal.class);
                sub.select(r.get("id")).where(cb.equal(r.get("payment"), root));
                return reversed ? cb.exists(sub) : cb.not(cb.exists(sub));
            });
        }
        if (search != null && !search.isBlank()) {
            String pattern = SearchSpecs.likePattern(search);
            spec = spec.and((root, query, cb) -> {
                var student = root.join("student");
                var user = student.join("user");
                return cb.or(cb.like(cb.lower(root.get("receiptNumber")), pattern, '\\'),
                        cb.like(cb.lower(student.get("admissionNumber")), pattern, '\\'),
                        cb.like(cb.lower(cb.concat(cb.concat(user.get("firstName"), " "), user.get("lastName"))),
                                pattern, '\\'));
            });
        }
        Page<Payment> page = payments.findAll(spec, pageable);
        List<FeeDtos.PaymentResponse> views = mapper.payments(page.getContent());
        return new PageResponse<>(views, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public FeeDtos.PaymentResponse get(UUID schoolId, UUID id) {
        return one(find(schoolId, id));
    }

    @Transactional(readOnly = true)
    public FeeDtos.Receipt receipt(UUID schoolId, UUID id) {
        return receiptOf(find(schoolId, id));
    }

    @Transactional(readOnly = true)
    public FeeDtos.Receipt myReceipt(AuthenticatedUser actor, UUID id) {
        StudentProfile student = studentFees.profile(actor);
        Payment payment = find(actor.schoolId(), id);
        if (!payment.getStudent().getId().equals(student.getId())) {
            throw new ResourceNotFoundException(ENTITY);
        }
        return receiptOf(payment);
    }

    private Payment find(UUID schoolId, UUID id) {
        return payments.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException(ENTITY));
    }

    private FeeDtos.PaymentResponse one(Payment payment) {
        return mapper.payments(List.of(payment)).get(0);
    }

    private FeeDtos.Receipt receiptOf(Payment p) {
        StudentFee fee = p.getStudentFee();
        FeeDtos.PaymentResponse view = one(p);
        String school = schools.findById(p.getSchoolId()).map(s -> s.getName()).orElse("");
        return new FeeDtos.Receipt(p.getReceiptNumber(), school, view.studentName(),
                p.getStudent().getAdmissionNumber(), mapper.className(p.getStudent().getId()),
                fee.getAcademicYear().getName(), fee.getCategoryName(), fee.getDescription(), fee.getAmount(),
                p.getAmount(), p.getMethod(), p.getReference(), p.getPaidOn(), view.receivedByName(), fee.balance(),
                view.reversed(), view.reversalReason(), view.reversedAt());
    }
}
