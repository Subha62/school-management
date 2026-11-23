package com.schoolmanagement.fee.controller;

import com.schoolmanagement.common.api.PageRequestFactory;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.service.PaymentService;
import com.schoolmanagement.fee.service.StudentFeeService;
import com.schoolmanagement.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A student's own fees and receipts. There are no ids of other students anywhere in these paths. */
@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasAuthority('ROLE_STUDENT')")
@Tag(name = "Fees (students)")
public class StudentFeeController {

    private static final Set<String> SORTABLE = Set.of("dueDate", "amount", "createdAt");

    private final StudentFeeService fees;
    private final PaymentService payments;
    private final PageRequestFactory pageRequests;

    public StudentFeeController(StudentFeeService fees, PaymentService payments, PageRequestFactory pageRequests) {
        this.fees = fees;
        this.payments = payments;
        this.pageRequests = pageRequests;
    }

    @GetMapping("/fees")
    @Operation(summary = "My fees, soonest due first")
    public PageResponse<FeeDtos.StudentFeeResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) FeeStatus status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        var pageable = pageRequests.create(page, size, sort, direction, SORTABLE, Sort.by("dueDate"));
        return fees.mine(actor, status, pageable);
    }

    @GetMapping("/fees/{id}")
    @Operation(summary = "One of my fees with its payments")
    public FeeDtos.StudentFeeDetail get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return fees.mineDetail(actor, id);
    }

    @GetMapping("/payments/{id}/receipt")
    @Operation(summary = "The receipt of one of my payments")
    public FeeDtos.Receipt receipt(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID id) {
        return payments.myReceipt(actor, id);
    }
}
