package com.schoolmanagement.fee.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.FeeCategory;
import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.entity.PaymentMethod;
import com.schoolmanagement.fee.service.FeeConfigService;
import com.schoolmanagement.fee.service.FeeReportService;
import com.schoolmanagement.fee.service.PaymentService;
import com.schoolmanagement.fee.service.StudentFeeService;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All financial data is for administrators only: teachers have no fee endpoints and are refused here. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Fees (administrators)")
public class AdminFeeController {

    private static final Set<String> CATEGORY_SORT = Set.of("name", "active", "createdAt");
    private static final Set<String> STRUCTURE_SORT = Set.of("dueDate", "amount", "createdAt");
    private static final Set<String> FEE_SORT = Set.of("dueDate", "amount", "paidAmount", "createdAt");
    private static final Set<String> PAYMENT_SORT = Set.of("paidOn", "amount", "createdAt", "receiptNumber");

    private final FeeConfigService config;
    private final StudentFeeService studentFees;
    private final PaymentService payments;
    private final FeeReportService reports;
    private final PageRequestFactory pageRequests;

    public AdminFeeController(FeeConfigService config, StudentFeeService studentFees, PaymentService payments,
            FeeReportService reports, PageRequestFactory pageRequests) {
        this.config = config;
        this.studentFees = studentFees;
        this.payments = payments;
        this.reports = reports;
        this.pageRequests = pageRequests;
    }

    // ---- categories -------------------------------------------------------------------------------------

    @GetMapping("/fee-categories")
    public PageResponse<FeeDtos.CategoryResponse> categories(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, CATEGORY_SORT, Sort.by("name"));
        return PageResponse.from(config.searchCategories(actor.schoolId(), active, search, pageable),
                AdminFeeController::toResponse);
    }

    @PostMapping("/fee-categories")
    public ResponseEntity<FeeDtos.CategoryResponse> createCategory(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody FeeDtos.CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(config.createCategory(actor, request)));
    }

    @PutMapping("/fee-categories/{id}")
    public FeeDtos.CategoryResponse updateCategory(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @Valid @RequestBody FeeDtos.CategoryRequest request) {
        return toResponse(config.updateCategory(actor, id, request));
    }

    @DeleteMapping("/fee-categories/{id}")
    @Operation(summary = "Delete a category that no fee structure uses")
    public ResponseEntity<Void> deleteCategory(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        config.deleteCategory(actor, id);
        return ResponseEntity.noContent().build();
    }

    private static FeeDtos.CategoryResponse toResponse(FeeCategory c) {
        return new FeeDtos.CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive(), c.getVersion());
    }

    // ---- structures -------------------------------------------------------------------------------------

    @GetMapping("/fee-structures")
    public PageResponse<FeeDtos.StructureResponse> structures(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) UUID feeCategoryId, @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, STRUCTURE_SORT,
                Sort.by("dueDate").and(Sort.by("createdAt")));
        return config.searchStructures(actor.schoolId(), academicYearId, gradeLevelId, feeCategoryId, active,
                pageable);
    }

    @GetMapping("/fee-structures/{id}")
    public FeeDtos.StructureResponse structure(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return config.getStructure(actor.schoolId(), id);
    }

    @PostMapping("/fee-structures")
    public ResponseEntity<FeeDtos.StructureResponse> createStructure(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody FeeDtos.StructureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(config.createStructure(actor, request));
    }

    @PutMapping("/fee-structures/{id}")
    @Operation(summary = "Edit a structure; amount and due date are frozen once fees were generated from it")
    public FeeDtos.StructureResponse updateStructure(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @Valid @RequestBody FeeDtos.StructureRequest request) {
        return config.updateStructure(actor, id, request);
    }

    @DeleteMapping("/fee-structures/{id}")
    public ResponseEntity<Void> deleteStructure(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        config.deleteStructure(actor, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/fee-structures/{id}/generate")
    @Operation(summary = "Bill every enrolled student of the grade who has not been billed yet (repeatable)")
    public FeeDtos.GenerateResult generate(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return config.generate(actor, id);
    }

    // ---- student fees -----------------------------------------------------------------------------------

    @GetMapping("/student-fees")
    @Operation(summary = "Student fees (paged, filterable by year, grade, category, student, status or name)")
    public PageResponse<FeeDtos.StudentFeeResponse> studentFees(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID gradeLevelId,
            @RequestParam(required = false) UUID feeCategoryId, @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) FeeStatus status, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, FEE_SORT, Sort.by("dueDate"));
        return studentFees.search(actor.schoolId(), academicYearId, gradeLevelId, feeCategoryId, studentId, status,
                search, pageable);
    }

    @GetMapping("/student-fees/{id}")
    public FeeDtos.StudentFeeDetail studentFee(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id) {
        return studentFees.get(actor.schoolId(), id);
    }

    @PostMapping("/student-fees/{id}/payments")
    @Operation(summary = "Record a payment: positive, within the balance; returns the payment with its receipt number")
    public ResponseEntity<FeeDtos.PaymentResponse> pay(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID id, @Valid @RequestBody FeeDtos.PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payments.record(actor, id, request));
    }

    @PostMapping("/student-fees/{id}/waive")
    public FeeDtos.StudentFeeDetail waive(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody FeeDtos.ReasonRequest request) {
        return studentFees.waive(actor, id, request);
    }

    @PostMapping("/student-fees/{id}/unwaive")
    public FeeDtos.StudentFeeDetail unwaive(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody FeeDtos.ReasonRequest request) {
        return studentFees.unwaive(actor, id, request);
    }

    // ---- payments ---------------------------------------------------------------------------------------

    @GetMapping("/payments")
    @Operation(summary = "Payments (paged, filterable by date, method, student, reversal state or receipt/name)")
    public PageResponse<FeeDtos.PaymentResponse> payments(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) PaymentMethod method, @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) Boolean reversed, @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, PAYMENT_SORT,
                Sort.by(Sort.Direction.DESC, "paidOn").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        return payments.search(actor.schoolId(), from, to, method, studentId, reversed, search, pageable);
    }

    @GetMapping("/payments/{id}")
    public FeeDtos.PaymentResponse payment(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return payments.get(actor.schoolId(), id);
    }

    @GetMapping("/payments/{id}/receipt")
    public FeeDtos.Receipt receipt(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return payments.receipt(actor.schoolId(), id);
    }

    @PostMapping("/payments/{id}/reverse")
    @Operation(summary = "Reverse a payment with a reason; the payment is kept and the fee balance is restored")
    public FeeDtos.PaymentResponse reverse(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id,
            @Valid @RequestBody FeeDtos.ReasonRequest request) {
        return payments.reverse(actor, id, request);
    }

    // ---- reports ----------------------------------------------------------------------------------------

    @GetMapping("/fee-reports/summary")
    @Operation(summary = "Billed, collected, outstanding and overdue totals for a year (optionally one grade)")
    public FeeDtos.Summary summary(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam UUID academicYearId, @RequestParam(required = false) UUID gradeLevelId) {
        return reports.summary(actor.schoolId(), academicYearId, gradeLevelId);
    }

    @GetMapping("/fee-reports/collections")
    @Operation(summary = "Money received between two dates (default: this month), net of reversals")
    public FeeDtos.Collections collections(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.collections(actor.schoolId(), from, to);
    }
}
