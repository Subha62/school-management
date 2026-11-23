package com.schoolmanagement.fee.entity;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One charge to one student. The amount, category name and due date are copied from the structure when the fee is
 * generated. {@code paidAmount} is the sum of the payments that have not been reversed; it changes only through
 * {@link #applyPayment} and {@link #takeBack}, while the row is locked.
 */
@Entity
@Table(name = "student_fees")
public class StudentFee extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_structure_id", nullable = false, updatable = false)
    private FeeStructure structure;

    @Column(nullable = false, updatable = false)
    private String categoryName;

    @Column(updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal paidAmount = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean waived;

    private String waiveReason;
    private UUID waivedBy;
    private Instant waivedAt;

    protected StudentFee() {
    }

    public StudentFee(StudentProfile student, FeeStructure structure) {
        this.schoolId = structure.getSchoolId();
        this.student = student;
        this.academicYear = structure.getAcademicYear();
        this.structure = structure;
        this.categoryName = structure.getCategory().getName();
        this.description = structure.getDescription();
        this.amount = structure.getAmount();
        this.dueDate = structure.getDueDate();
    }

    public BigDecimal balance() {
        return amount.subtract(paidAmount);
    }

    public void applyPayment(BigDecimal payment) {
        if (waived) {
            throw new BusinessRuleException("This fee has been waived, so it takes no payments.");
        }
        if (payment.compareTo(balance()) > 0) {
            throw new BusinessRuleException("The payment is more than the balance of " + balance().toPlainString()
                    + ".");
        }
        paidAmount = paidAmount.add(payment);
    }

    public void takeBack(BigDecimal payment) {
        paidAmount = paidAmount.subtract(payment);
    }

    public void waive(String reason, UUID by, Instant at) {
        if (waived) {
            throw new BusinessRuleException("This fee is already waived.");
        }
        if (paidAmount.signum() != 0) {
            throw new BusinessRuleException("Payments have been received for this fee. Reverse them before waiving it.");
        }
        waived = true;
        waiveReason = reason;
        waivedBy = by;
        waivedAt = at;
    }

    public void unwaive() {
        if (!waived) {
            throw new BusinessRuleException("This fee is not waived.");
        }
        waived = false;
        waiveReason = null;
        waivedBy = null;
        waivedAt = null;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public FeeStructure getStructure() {
        return structure;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public boolean isWaived() {
        return waived;
    }

    public String getWaiveReason() {
        return waiveReason;
    }

    public Instant getWaivedAt() {
        return waivedAt;
    }
}
