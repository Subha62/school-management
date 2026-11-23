package com.schoolmanagement.fee.service;

import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.Payment;
import com.schoolmanagement.fee.entity.PaymentReversal;
import com.schoolmanagement.fee.entity.StudentFee;
import com.schoolmanagement.fee.repository.PaymentReversalRepository;
import com.schoolmanagement.config.FeeProperties;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Builds the API view of fees and payments, loading class names, staff names and reversals in batches. */
@Component
public class FeeMapper {

    private final StudentEnrollmentRepository enrollments;
    private final PaymentReversalRepository reversals;
    private final TeacherDirectory directory;
    private final FeeProperties properties;

    public FeeMapper(StudentEnrollmentRepository enrollments, PaymentReversalRepository reversals,
            TeacherDirectory directory, FeeProperties properties) {
        this.enrollments = enrollments;
        this.reversals = reversals;
        this.directory = directory;
        this.properties = properties;
    }

    public List<FeeDtos.StudentFeeResponse> fees(Collection<StudentFee> fees, LocalDate today) {
        if (fees.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> classes = classNames(fees.stream().map(f -> f.getStudent().getId()).collect(Collectors.toSet()));
        return fees.stream().map(f -> fee(f, classes.get(f.getStudent().getId()), today)).toList();
    }

    public FeeDtos.StudentFeeResponse fee(StudentFee f, String className, LocalDate today) {
        StudentProfile student = f.getStudent();
        return new FeeDtos.StudentFeeResponse(f.getId(), student.getId(), student.getAdmissionNumber(), name(student),
                className, f.getAcademicYear().getId(), f.getAcademicYear().getName(), f.getStructure().getId(),
                f.getCategoryName(), f.getDescription(), f.getAmount(), f.getPaidAmount(), f.balance(), f.getDueDate(),
                FeeStatusRules.of(f, today, properties.dueSoonDays()), f.getWaiveReason(), f.getWaivedAt(),
                f.getVersion());
    }

    public List<FeeDtos.PaymentResponse> payments(Collection<Payment> payments) {
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<UUID, PaymentReversal> reversed = reversals
                .findAllByPaymentIds(payments.stream().map(Payment::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getPayment().getId(), Function.identity()));
        Set<UUID> staff = new HashSet<>();
        payments.forEach(p -> staff.add(p.getReceivedBy()));
        reversed.values().forEach(r -> staff.add(r.getReversedBy()));
        Map<UUID, String> names = directory.displayNames(staff);
        return payments.stream().map(p -> {
            PaymentReversal r = reversed.get(p.getId());
            StudentProfile student = p.getStudent();
            return new FeeDtos.PaymentResponse(p.getId(), p.getReceiptNumber(), p.getStudentFee().getId(),
                    student.getId(), student.getAdmissionNumber(), name(student),
                    p.getStudentFee().getCategoryName(), p.getAmount(), p.getMethod(), p.getReference(), p.getPaidOn(),
                    names.get(p.getReceivedBy()), p.getCreatedAt(), r != null, r == null ? null : r.getReason(),
                    r == null ? null : r.getReversedAt(), r == null ? null : names.get(r.getReversedBy()));
        }).toList();
    }

    public String className(UUID studentId) {
        return classNames(Set.of(studentId)).get(studentId);
    }

    private Map<UUID, String> classNames(Set<UUID> studentIds) {
        Map<UUID, String> names = new HashMap<>();
        for (StudentEnrollment e : enrollments.findActiveByStudentIds(studentIds)) {
            names.putIfAbsent(e.getStudent().getId(), e.getGradeLevel().getName() + " - " + e.getSection().getName());
        }
        return names;
    }

    static String name(StudentProfile student) {
        return (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim();
    }
}
