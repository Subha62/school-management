package com.schoolmanagement.fee.dto;

import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FeeDtos {

    private FeeDtos() {
    }

    // ---- configuration ----------------------------------------------------------------------------------

    public record CategoryRequest(
            @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
            @Size(max = 300, message = "Description must be at most 300 characters") String description,
            @NotNull(message = "Active flag is required") Boolean active,
            Long version) {
    }

    public record CategoryResponse(UUID id, String name, String description, boolean active, long version) {
    }

    /**
     * {@code academicYearId}, {@code feeCategoryId} and {@code gradeLevelId} are used when creating; once fees
     * have been generated from a structure, only the description and the active flag can change.
     */
    public record StructureRequest(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID feeCategoryId,
            @Size(max = 200, message = "Description must be at most 200 characters") String description,
            @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be more than 0") @DecimalMax(value = "9999999999.99", message = "Amount is too large") @Digits(integer = 10, fraction = 2, message = "Use at most two decimal places") BigDecimal amount,
            @NotNull(message = "Due date is required") LocalDate dueDate,
            @NotNull(message = "Active flag is required") Boolean active,
            Long version) {
    }

    public record StructureResponse(UUID id, UUID academicYearId, String academicYearName, UUID gradeLevelId,
            String gradeLevelName, UUID feeCategoryId, String categoryName, String description, BigDecimal amount,
            LocalDate dueDate, boolean active, long billedStudents, long version) {
    }

    public record GenerateResult(int created, int alreadyBilled) {
    }

    // ---- student fees -----------------------------------------------------------------------------------

    public record StudentFeeResponse(UUID id, UUID studentId, String admissionNumber, String studentName,
            String className, UUID academicYearId, String academicYearName, UUID structureId, String categoryName,
            String description, BigDecimal amount, BigDecimal paidAmount, BigDecimal balance, LocalDate dueDate,
            FeeStatus status, String waiveReason, Instant waivedAt, long version) {
    }

    public record PaymentResponse(UUID id, String receiptNumber, UUID studentFeeId, UUID studentId,
            String admissionNumber, String studentName, String categoryName, BigDecimal amount, PaymentMethod method,
            String reference, LocalDate paidOn, String receivedByName, Instant createdAt, boolean reversed,
            String reversalReason, Instant reversedAt, String reversedByName) {
    }

    public record StudentFeeDetail(StudentFeeResponse fee, List<PaymentResponse> payments) {
    }

    public record PaymentRequest(
            @NotNull(message = "Amount is required") @DecimalMin(value = "0.01", message = "Amount must be more than 0") @DecimalMax(value = "9999999999.99", message = "Amount is too large") @Digits(integer = 10, fraction = 2, message = "Use at most two decimal places") BigDecimal amount,
            @NotNull(message = "Payment method is required") PaymentMethod method,
            @Size(max = 100, message = "Reference must be at most 100 characters") String reference,
            @NotNull(message = "Payment date is required") LocalDate paidOn) {
    }

    public record ReasonRequest(
            @NotBlank(message = "A reason is required") @Size(min = 5, max = 300, message = "The reason must be 5 to 300 characters") String reason) {
    }

    /** What is printed for a payment. {@code balance} is the fee's balance now, not at the time of payment. */
    public record Receipt(String receiptNumber, String schoolName, String studentName, String admissionNumber,
            String className, String academicYearName, String categoryName, String description, BigDecimal feeAmount,
            BigDecimal amount, PaymentMethod method, String reference, LocalDate paidOn, String receivedByName,
            BigDecimal balance, boolean reversed, String reversalReason, Instant reversedAt) {
    }

    // ---- reports ----------------------------------------------------------------------------------------

    public record CategoryTotals(String categoryName, BigDecimal billed, BigDecimal collected,
            BigDecimal outstanding) {
    }

    /**
     * {@code billed} excludes waived fees (reported separately); {@code outstanding = billed - collected};
     * {@code overdue} is the part of outstanding that is past its due date.
     */
    public record Summary(UUID academicYearId, UUID gradeLevelId, BigDecimal billed, BigDecimal collected,
            BigDecimal outstanding, BigDecimal overdue, BigDecimal waived, Map<FeeStatus, Long> countByStatus,
            List<CategoryTotals> byCategory) {
    }

    public record MethodTotal(PaymentMethod method, long count, BigDecimal total) {
    }

    /** Money received in a period, net of payments that were reversed. */
    public record Collections(LocalDate from, LocalDate to, long payments, BigDecimal received,
            BigDecimal reversed, BigDecimal net, List<MethodTotal> byMethod) {
    }
}
