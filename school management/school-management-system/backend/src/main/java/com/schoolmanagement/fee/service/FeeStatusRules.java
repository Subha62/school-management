package com.schoolmanagement.fee.service;

import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.entity.StudentFee;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/**
 * The fee status, derived from facts that never go stale (amount, paid amount, due date, waiver) and today's date,
 * so no job has to roll fees from "due" to "overdue". {@link #of} and {@link #matching} must agree; the tests check
 * that they do.
 *
 * <p>Precedence: waived, paid in full, overdue (past its due date with a balance), partially paid, due (nothing paid
 * and due within {@code dueSoonDays}), and finally not due.
 */
public final class FeeStatusRules {

    private FeeStatusRules() {
    }

    public static FeeStatus of(StudentFee fee, LocalDate today, int dueSoonDays) {
        return of(fee.isWaived(), fee.getAmount(), fee.getPaidAmount(), fee.getDueDate(), today, dueSoonDays);
    }

    public static FeeStatus of(boolean waived, BigDecimal amount, BigDecimal paid, LocalDate dueDate,
            LocalDate today, int dueSoonDays) {
        if (waived) {
            return FeeStatus.WAIVED;
        }
        if (paid.compareTo(amount) >= 0) {
            return FeeStatus.PAID;
        }
        if (dueDate.isBefore(today)) {
            return FeeStatus.OVERDUE;
        }
        if (paid.signum() > 0) {
            return FeeStatus.PARTIALLY_PAID;
        }
        return dueDate.isAfter(today.plusDays(dueSoonDays)) ? FeeStatus.NOT_DUE : FeeStatus.DUE;
    }

    public static Specification<StudentFee> matching(FeeStatus status, LocalDate today, int dueSoonDays) {
        if (status == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> {
            var waived = root.<Boolean>get("waived");
            var amount = root.<BigDecimal>get("amount");
            var paid = root.<BigDecimal>get("paidAmount");
            var due = root.<LocalDate>get("dueDate");
            Predicate open = cb.isFalse(waived);
            Predicate settled = cb.greaterThanOrEqualTo(paid, amount);
            Predicate unsettled = cb.lessThan(paid, amount);
            Predicate late = cb.lessThan(due, today);
            Predicate notLate = cb.greaterThanOrEqualTo(due, today);
            Predicate nothingPaid = cb.equal(paid, BigDecimal.ZERO);
            return switch (status) {
                case WAIVED -> cb.isTrue(waived);
                case PAID -> cb.and(open, settled);
                case OVERDUE -> cb.and(open, unsettled, late);
                case PARTIALLY_PAID -> cb.and(open, unsettled, notLate, cb.greaterThan(paid, BigDecimal.ZERO));
                case DUE -> cb.and(open, nothingPaid, notLate,
                        cb.lessThanOrEqualTo(due, today.plusDays(dueSoonDays)));
                case NOT_DUE -> cb.and(open, nothingPaid, cb.greaterThan(due, today.plusDays(dueSoonDays)));
            };
        };
    }
}
