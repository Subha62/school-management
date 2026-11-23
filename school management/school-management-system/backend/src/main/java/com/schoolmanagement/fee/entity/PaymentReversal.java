package com.schoolmanagement.fee.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cancels one payment. Written once; the payment it points to keeps its original details. */
@Entity
@Table(name = "payment_reversals")
public class PaymentReversal extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private UUID reversedBy;

    @Column(nullable = false, updatable = false)
    private Instant reversedAt;

    protected PaymentReversal() {
    }

    public PaymentReversal(Payment payment, String reason, UUID reversedBy, Instant reversedAt) {
        this.payment = payment;
        this.reason = reason;
        this.reversedBy = reversedBy;
        this.reversedAt = reversedAt;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getReason() {
        return reason;
    }

    public UUID getReversedBy() {
        return reversedBy;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }
}
