package com.schoolmanagement.fee.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Money received. A payment is never changed or deleted; a mistaken one is reversed (see {@link PaymentReversal}). */
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_fee_id", nullable = false, updatable = false)
    private StudentFee studentFee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(nullable = false, updatable = false)
    private String receiptNumber;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentMethod method;

    @Column(updatable = false)
    private String reference;

    @Column(nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(nullable = false, updatable = false)
    private UUID receivedBy;

    protected Payment() {
    }

    public Payment(StudentFee studentFee, String receiptNumber, BigDecimal amount, PaymentMethod method,
            String reference, LocalDate paidOn, UUID receivedBy) {
        this.schoolId = studentFee.getSchoolId();
        this.studentFee = studentFee;
        this.student = studentFee.getStudent();
        this.receiptNumber = receiptNumber;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.paidOn = paidOn;
        this.receivedBy = receivedBy;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public StudentFee getStudentFee() {
        return studentFee;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public UUID getReceivedBy() {
        return receivedBy;
    }
}
