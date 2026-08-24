package com.project.backend.api.rest;

import com.project.backend.api.dto.request.CreateLoanRequest;
import com.project.backend.api.dto.response.LoanResponse;
import com.project.backend.api.dto.response.LoanSummaryResponse;
import com.project.backend.api.dto.response.PaymentSummaryResponse;
import com.project.backend.api.mapper.ApiMapper;
import com.project.backend.application.command.CreateLoanCommand;
import com.project.backend.application.dto.LoanCreated;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.query.ListLenderLoansQuery;
import com.project.backend.application.query.ListPayerLoansQuery;
import com.project.backend.application.query.ListPendingPaymentsQuery;
import com.project.backend.domain.valueobject.LoanId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v1/loans", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Loans")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {
    private final CurrentAccountResolver currentAccount;
    private final CommandHandler<CreateLoanCommand, LoanCreated> createLoan;
    private final QueryHandler<ListLenderLoansQuery, List<LoanSummary>> listLenderLoans;
    private final QueryHandler<ListPayerLoansQuery, List<LoanSummary>> listPayerLoans;
    private final QueryHandler<ListPendingPaymentsQuery, List<PaymentSummary>> listPendingPayments;

    public LoanController(
            CurrentAccountResolver currentAccount,
            CommandHandler<CreateLoanCommand, LoanCreated> createLoan,
            QueryHandler<ListLenderLoansQuery, List<LoanSummary>> listLenderLoans,
            QueryHandler<ListPayerLoansQuery, List<LoanSummary>> listPayerLoans,
            QueryHandler<ListPendingPaymentsQuery, List<PaymentSummary>> listPendingPayments) {
        this.currentAccount = currentAccount;
        this.createLoan = createLoan;
        this.listLenderLoans = listLenderLoans;
        this.listPayerLoans = listPayerLoans;
        this.listPendingPayments = listPendingPayments;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a loan proposal", description = "Requires the LENDER role.")
    @ApiResponse(responseCode = "201", description = "Proposal created")
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody CreateLoanRequest request, Principal principal) {
        LoanResponse response = ApiMapper.response(createLoan.execute(ApiMapper.toCommand(request, currentAccount.requireAccountId(principal))));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{loanId}").buildAndExpand(response.loanId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping(path = "/lender")
    @Operation(summary = "List loans for the authenticated lender")
    public List<LoanSummaryResponse> listForLender(Principal principal) {
        return ApiMapper.responsesForLoans(listLenderLoans.execute(new ListLenderLoansQuery(currentAccount.requireAccountId(principal))));
    }

    @GetMapping(path = "/payer")
    @Operation(summary = "List loans for the authenticated payer")
    public List<LoanSummaryResponse> listForPayer(Principal principal) {
        return ApiMapper.responsesForLoans(listPayerLoans.execute(new ListPayerLoansQuery(currentAccount.requireAccountId(principal))));
    }

    @GetMapping(path = "/{loanId}/payments/pending")
    @Operation(summary = "List payments pending lender review", description = "Requires the LENDER role and ownership of the loan.")
    public List<PaymentSummaryResponse> listPendingPayments(@PathVariable UUID loanId, Principal principal) {
        return ApiMapper.responsesForPayments(listPendingPayments.execute(
                new ListPendingPaymentsQuery(currentAccount.requireAccountId(principal), new LoanId(loanId))));
    }
}
