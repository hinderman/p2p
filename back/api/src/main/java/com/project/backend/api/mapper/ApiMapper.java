package com.project.backend.api.mapper;

import com.project.backend.api.dto.request.ApprovePaymentRequest;
import com.project.backend.api.dto.request.CreateLoanRequest;
import com.project.backend.api.dto.request.MoneyRequest;
import com.project.backend.api.dto.request.PaymentAllocationRequest;
import com.project.backend.api.dto.request.PaymentProofRequest;
import com.project.backend.api.dto.request.PaymentScheduleRequest;
import com.project.backend.api.dto.request.ReportPaymentRequest;
import com.project.backend.api.dto.response.AuthenticatedSessionResponse;
import com.project.backend.api.dto.response.LoanResponse;
import com.project.backend.api.dto.response.LoanSummaryResponse;
import com.project.backend.api.dto.response.MoneyResponse;
import com.project.backend.api.dto.response.PageResponse;
import com.project.backend.api.dto.response.PaymentResponse;
import com.project.backend.api.dto.response.PaymentSummaryResponse;
import com.project.backend.application.dto.Page;
import com.project.backend.application.command.ApprovePaymentCommand;
import com.project.backend.application.command.CreateLoanCommand;
import com.project.backend.application.command.ReportPaymentCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.dto.LoanAccepted;
import com.project.backend.application.dto.LoanCreated;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.application.dto.PaymentRegistered;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.payment.PaymentProof;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.InterestRate;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.loan.PaymentScheduleRule;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Stateless mapping between the HTTP contract and application input/output objects. */
public final class ApiMapper {
    private ApiMapper() { }

    public static CreateLoanCommand toCommand(CreateLoanRequest request, UserAccountId lenderAccountId) {
        return new CreateLoanCommand(lenderAccountId, new EmailAddress(request.payerEmail()), money(request.originalPrincipal()),
                new InterestRate(request.interestRatePercentage()), request.ratePeriod(), request.interestCalculationMethod(),
                request.dayCountBasis(), request.amortizationMethod(), request.capitalPrepaymentPolicy(),
                request.installmentCount(), request.firstDueDate(), ZoneId.of(request.timeZone()), schedule(request.paymentSchedule()));
    }

    public static ReportPaymentCommand toCommand(
            ReportPaymentRequest request, LoanId loanId, UserAccountId payerAccountId, UUID idempotencyKey) {
        return new ReportPaymentCommand(payerAccountId, loanId, request.paymentType(), money(request.reportedAmount()),
                request.reportedPaymentDate(), request.externalReference(), idempotencyKey,
                request.proofs().stream().map(ApiMapper::proof).toList());
    }

    public static ApprovePaymentCommand toCommand(
            ApprovePaymentRequest request, ReportedPaymentId paymentId, UserAccountId lenderAccountId) {
        return new ApprovePaymentCommand(lenderAccountId, paymentId, money(request.validatedAmount()),
                request.allocations().stream().map(ApiMapper::allocation).toList());
    }

    public static AuthenticatedSessionResponse response(AuthenticatedSession session) {
        return new AuthenticatedSessionResponse(session.userAccountId().value(), session.personId().value(), session.roles(),
                session.accessToken(), session.refreshToken(), session.accessTokenExpiresAt());
    }

    public static LoanResponse response(LoanCreated loan) {
        return new LoanResponse(loan.loanId().value(), loan.status());
    }

    public static LoanResponse response(LoanAccepted loan) {
        return new LoanResponse(loan.loanId().value(), loan.status());
    }

    public static PaymentResponse response(PaymentRegistered payment) {
        return new PaymentResponse(payment.reportedPaymentId().value(), payment.status());
    }

    public static PaymentResponse response(PaymentProcessed payment) {
        return new PaymentResponse(payment.reportedPaymentId().value(), payment.status());
    }

    public static List<LoanSummaryResponse> responsesForLoans(List<LoanSummary> loans) {
        return loans.stream().map(loan -> new LoanSummaryResponse(loan.loanId().value(), loan.counterpartyPersonId().value(),
                loan.status(), response(loan.originalPrincipal()), response(loan.outstandingBalance()), loan.createdAt())).toList();
    }

    public static PageResponse<PaymentSummaryResponse> responsesForPayments(Page<PaymentSummary> payments) {
        return response(payments.map(ApiMapper::response));
    }

    private static PaymentSummaryResponse response(PaymentSummary payment) {
        return new PaymentSummaryResponse(payment.reportedPaymentId().value(), payment.loanId().value(),
                payment.status(), response(payment.reportedAmount()),
                payment.validatedAmount() == null ? null : response(payment.validatedAmount()),
                payment.reportedPaymentDate());
    }

    private static <T> PageResponse<T> response(Page<T> page) {
        return new PageResponse<>(page.content(), page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.hasNext());
    }

    private static Money money(MoneyRequest request) {
        return new Money(request.amount(), request.currency());
    }

    private static PaymentScheduleRule schedule(PaymentScheduleRequest request) {
        return new PaymentScheduleRule(request.frequency(), request.intervalDays(), request.daysOfMonth(), request.nonBusinessDayAdjustment());
    }

    private static PaymentProof proof(PaymentProofRequest request) {
        return new PaymentProof(request.storedObjectId(), request.sha256());
    }

    private static PaymentAllocation allocation(PaymentAllocationRequest request) {
        return new PaymentAllocation(request.installmentId() == null ? null : new InstallmentId(request.installmentId()),
                request.type(), money(request.amount()));
    }

    private static MoneyResponse response(Money money) {
        return new MoneyResponse(money.amount(), money.currency());
    }
}
