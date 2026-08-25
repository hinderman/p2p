package com.project.backend.api.rest;

import com.project.backend.api.dto.request.ApprovePaymentRequest;
import com.project.backend.api.dto.request.ReasonRequest;
import com.project.backend.api.dto.request.ReportPaymentRequest;
import com.project.backend.api.dto.response.PaymentResponse;
import com.project.backend.api.mapper.ApiMapper;
import com.project.backend.application.command.ApprovePaymentCommand;
import com.project.backend.application.command.RejectPaymentCommand;
import com.project.backend.application.command.ReportPaymentCommand;
import com.project.backend.application.command.ReversePaymentCommand;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    private final CurrentAccountResolver currentAccount;
    private final ApplicationMediator mediator;

    public PaymentController(CurrentAccountResolver currentAccount, ApplicationMediator mediator) {
        this.currentAccount = currentAccount;
        this.mediator = mediator;
    }

    @PostMapping(path = "/loans/{loanId}/payments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Report an external payment", description = "Requires the PAYER role and payment proof references.")
    public PaymentResponse report(
            @PathVariable UUID loanId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReportPaymentRequest request,
            Principal principal) {
        return ApiMapper.response(mediator.send(ApiMapper.toCommand(
                request, new LoanId(loanId), currentAccount.requireAccountId(principal), UUID.fromString(idempotencyKey))));
    }

    @PostMapping(path = "/payments/{paymentId}/approval", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Approve a reported payment", description = "Requires the LENDER role and ownership of the loan.")
    public PaymentResponse approve(@PathVariable UUID paymentId, @Valid @RequestBody ApprovePaymentRequest request, Principal principal) {
        return ApiMapper.response(mediator.send(ApiMapper.toCommand(
                request, new ReportedPaymentId(paymentId), currentAccount.requireAccountId(principal))));
    }

    @PostMapping(path = "/payments/{paymentId}/rejection", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reject a reported payment", description = "Requires the LENDER role and a review reason.")
    public PaymentResponse reject(@PathVariable UUID paymentId, @Valid @RequestBody ReasonRequest request, Principal principal) {
        return ApiMapper.response(mediator.send(new RejectPaymentCommand(
                currentAccount.requireAccountId(principal), new ReportedPaymentId(paymentId), request.reason())));
    }

    @PostMapping(path = "/payments/{paymentId}/reversal", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reverse an approved payment", description = "Requires the LENDER role and a reversal reason.")
    public PaymentResponse reverse(@PathVariable UUID paymentId, @Valid @RequestBody ReasonRequest request, Principal principal) {
        return ApiMapper.response(mediator.send(new ReversePaymentCommand(
                currentAccount.requireAccountId(principal), new ReportedPaymentId(paymentId), request.reason())));
    }
}
