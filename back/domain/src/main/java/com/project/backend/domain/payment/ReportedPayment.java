package com.project.backend.domain.payment;

import com.project.backend.domain.entity.AggregateRoot;
import com.project.backend.domain.event.PaymentApproved;
import com.project.backend.domain.event.PaymentSubmittedForReview;
import com.project.backend.domain.event.PaymentRejected;
import com.project.backend.domain.event.PaymentReversed;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate for an external payment. A payment changes the debt only when the
 * lender approves it and its allocations equal the validated amount.
 */
public final class ReportedPayment extends AggregateRoot<ReportedPaymentId> {
    private final LoanId loanId;
    private final PersonId payerPersonId;
    private final UserAccountId reportingAccountId;
    private final PaymentType type;
    private final Money reportedAmount;
    private final LocalDate reportedPaymentDate;
    private final String externalReference;
    private final UUID idempotencyKey;
    private final Instant createdAt;
    private final List<PaymentProof> proofs = new ArrayList<>();
    private List<PaymentAllocation> allocations = List.of();
    private ReportedPaymentStatus status;
    private Money validatedAmount;
    private String rejectionReason;
    private String reversalReason;

    private ReportedPayment(
            ReportedPaymentId id,
            LoanId loanId,
            PersonId payerPersonId,
            UserAccountId reportingAccountId,
            PaymentType type,
            Money reportedAmount,
            LocalDate reportedPaymentDate,
            String externalReference,
            UUID idempotencyKey,
            ReportedPaymentStatus status,
            Instant createdAt) {
        super(id);
        this.loanId = Objects.requireNonNull(loanId, "The loan is required");
        this.payerPersonId = Objects.requireNonNull(payerPersonId, "The payer is required");
        this.reportingAccountId = Objects.requireNonNull(reportingAccountId, "The reporting account is required");
        this.type = Objects.requireNonNull(type, "The payment type is required");
        this.reportedAmount = Objects.requireNonNull(reportedAmount, "The reported amount is required");
        if (!reportedAmount.isPositive()) {
            throw new DomainRuleViolation("The reported amount must be positive");
        }
        this.reportedPaymentDate = Objects.requireNonNull(reportedPaymentDate, "The payment date is required");
        this.externalReference = externalReference == null ? null : externalReference.strip();
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "The idempotency key is required");
        this.status = Objects.requireNonNull(status, "The payment status is required");
        this.createdAt = Objects.requireNonNull(createdAt, "The creation time is required");
    }

    public static ReportedPayment create(
            ReportedPaymentId id,
            LoanId loanId,
            PersonId payerPersonId,
            UserAccountId reportingAccountId,
            PaymentType type,
            Money reportedAmount,
            LocalDate reportedPaymentDate,
            String externalReference,
            UUID idempotencyKey,
            Instant createdAt) {
        return new ReportedPayment(
                id, loanId, payerPersonId, reportingAccountId, type, reportedAmount,
                reportedPaymentDate, externalReference, idempotencyKey, ReportedPaymentStatus.DRAFT, createdAt);
    }

    /** Rehydrates the aggregate from persistence without producing domain events. */
    public static ReportedPayment rehydrate(
            ReportedPaymentId id,
            LoanId loanId,
            PersonId payerPersonId,
            UserAccountId reportingAccountId,
            PaymentType type,
            Money reportedAmount,
            LocalDate reportedPaymentDate,
            String externalReference,
            UUID idempotencyKey,
            ReportedPaymentStatus status,
            Instant createdAt,
            List<PaymentProof> proofs,
            List<PaymentAllocation> allocations,
            Money validatedAmount,
            String rejectionReason,
            String reversalReason) {
        ReportedPayment payment = new ReportedPayment(id, loanId, payerPersonId, reportingAccountId, type,
                reportedAmount, reportedPaymentDate, externalReference, idempotencyKey, status, createdAt);
        payment.proofs.addAll(List.copyOf(proofs));
        payment.allocations = List.copyOf(allocations);
        payment.validatedAmount = validatedAmount;
        payment.rejectionReason = rejectionReason;
        payment.reversalReason = reversalReason;
        return payment;
    }

    public LoanId loanId() { return loanId; }
    public PersonId payerPersonId() { return payerPersonId; }
    public UserAccountId reportingAccountId() { return reportingAccountId; }
    public PaymentType type() { return type; }
    public Money reportedAmount() { return reportedAmount; }
    public LocalDate reportedPaymentDate() { return reportedPaymentDate; }
    public String externalReference() { return externalReference; }
    public UUID idempotencyKey() { return idempotencyKey; }
    public ReportedPaymentStatus status() { return status; }
    public Money validatedAmount() { return validatedAmount; }
    public String rejectionReason() { return rejectionReason; }
    public String reversalReason() { return reversalReason; }
    public Instant createdAt() { return createdAt; }
    public List<PaymentProof> proofs() { return List.copyOf(proofs); }
    public List<PaymentAllocation> allocations() { return allocations; }

    /**
     * Indicates whether an idempotent retry is the same original submission.
     * A reused key with a different payload must never silently create or
     * return a different financial operation.
     */
    public boolean matchesSubmission(
            LoanId loanId,
            UserAccountId reportingAccountId,
            PaymentType type,
            Money reportedAmount,
            LocalDate reportedPaymentDate,
            String externalReference,
            List<PaymentProof> proofs) {
        return this.loanId.equals(loanId)
                && this.reportingAccountId.equals(reportingAccountId)
                && this.type == type
                && this.reportedAmount.equals(reportedAmount)
                && this.reportedPaymentDate.equals(reportedPaymentDate)
                && Objects.equals(this.externalReference, normalizeExternalReference(externalReference))
                && Set.copyOf(this.proofs).equals(Set.copyOf(proofs));
    }

    public void attachProof(PaymentProof proof) {
        Objects.requireNonNull(proof, "Payment proof is required");
        if (status != ReportedPaymentStatus.DRAFT) {
            throw new DomainRuleViolation("Proofs cannot be changed after the payment is submitted");
        }
        if (proofs.stream().anyMatch(existing -> existing.storedObjectId().equals(proof.storedObjectId()))) {
            throw new DomainRuleViolation("The proof is already attached to the payment");
        }
        proofs.add(proof);
    }

    public void submitForReview(Instant submittedAt) {
        Objects.requireNonNull(submittedAt, "The submission time is required");
        if (status != ReportedPaymentStatus.DRAFT) {
            throw new DomainRuleViolation("Only a draft payment can be submitted for review");
        }
        if (proofs.isEmpty()) {
            throw new DomainRuleViolation("At least one payment proof must be attached");
        }
        status = ReportedPaymentStatus.PENDING_REVIEW;
        registerEvent(new PaymentSubmittedForReview(id(), loanId, submittedAt));
    }

    public void approve(
            UserAccountId reviewerAccountId,
            Money validatedAmount,
            List<PaymentAllocation> allocations,
            Instant approvedAt) {
        Objects.requireNonNull(reviewerAccountId, "The reviewing account is required");
        Objects.requireNonNull(validatedAmount, "The validated amount is required");
        Objects.requireNonNull(allocations, "Payment allocations are required");
        Objects.requireNonNull(approvedAt, "The approval time is required");
        if (status != ReportedPaymentStatus.PENDING_REVIEW) {
            throw new DomainRuleViolation("Only a payment pending review can be approved");
        }
        if (reportingAccountId.equals(reviewerAccountId)) {
            throw new DomainRuleViolation("The payment reporter cannot approve it");
        }
        if (!validatedAmount.isPositive() || !validatedAmount.currency().equals(reportedAmount.currency())) {
            throw new DomainRuleViolation("The validated amount must be positive and use the payment currency");
        }
        List<PaymentAllocation> copy = List.copyOf(allocations);
        validateAllocations(copy, validatedAmount);
        this.validatedAmount = validatedAmount;
        this.allocations = copy;
        this.status = ReportedPaymentStatus.APPROVED;
        registerEvent(new PaymentApproved(id(), loanId, validatedAmount, approvedAt));
    }

    public void reject(UserAccountId reviewerAccountId, String reason, Instant rejectedAt) {
        Objects.requireNonNull(reviewerAccountId, "The reviewing account is required");
        Objects.requireNonNull(rejectedAt, "The rejection time is required");
        if (status != ReportedPaymentStatus.PENDING_REVIEW) {
            throw new DomainRuleViolation("Only a payment pending review can be rejected");
        }
        if (reportingAccountId.equals(reviewerAccountId)) {
            throw new DomainRuleViolation("The payment reporter cannot reject it");
        }
        reason = reason == null ? "" : reason.strip();
        if (reason.isEmpty()) {
            throw new DomainRuleViolation("Rejection requires a reason");
        }
        status = ReportedPaymentStatus.REJECTED;
        rejectionReason = reason;
        registerEvent(new PaymentRejected(id(), loanId, rejectedAt));
    }

    public void reverse(UserAccountId reviewerAccountId, String reason, Instant reversedAt) {
        Objects.requireNonNull(reviewerAccountId, "The reviewing account is required");
        Objects.requireNonNull(reversedAt, "The reversal time is required");
        if (status != ReportedPaymentStatus.APPROVED) {
            throw new DomainRuleViolation("Only an approved payment can be reversed");
        }
        if (reportingAccountId.equals(reviewerAccountId)) {
            throw new DomainRuleViolation("The payment reporter cannot reverse it");
        }
        reason = reason == null ? "" : reason.strip();
        if (reason.isEmpty()) {
            throw new DomainRuleViolation("Reversal requires a reason");
        }
        status = ReportedPaymentStatus.REVERSED;
        reversalReason = reason;
        registerEvent(new PaymentReversed(id(), loanId, reversedAt));
    }

    private void validateAllocations(List<PaymentAllocation> allocations, Money amount) {
        if (allocations.isEmpty()) {
            throw new DomainRuleViolation("An approved payment must be fully allocated");
        }
        Money total = Money.zero(amount.currency());
        for (PaymentAllocation allocation : allocations) {
            if (!amount.currency().equals(allocation.amount().currency())) {
                throw new DomainRuleViolation("All allocations must use the payment currency");
            }
            total = total.add(allocation.amount());
        }
        if (total.compareTo(amount) != 0) {
            throw new DomainRuleViolation("Allocations must equal the validated amount exactly");
        }
        boolean hasDirectPrincipalAllocation = allocations.stream()
                .anyMatch(allocation -> allocation.type() == PaymentAllocationType.DIRECT_PRINCIPAL);
        if (type == PaymentType.CAPITAL_PREPAYMENT && (allocations.size() != 1 || !hasDirectPrincipalAllocation)) {
            throw new DomainRuleViolation("A principal prepayment must be allocated once as direct principal");
        }
        if (type == PaymentType.INSTALLMENT && hasDirectPrincipalAllocation) {
            throw new DomainRuleViolation("An installment payment cannot include a direct principal allocation");
        }
        Set<AllocationTarget> targets = new HashSet<>();
        for (PaymentAllocation allocation : allocations) {
            AllocationTarget target = new AllocationTarget(allocation.installmentId(), allocation.type());
            if (!targets.add(target)) {
                throw new DomainRuleViolation("A payment cannot allocate the same installment component more than once");
            }
        }
    }

    private static String normalizeExternalReference(String externalReference) {
        return externalReference == null ? null : externalReference.strip();
    }

    private record AllocationTarget(com.project.backend.domain.valueobject.InstallmentId installmentId,
                                    PaymentAllocationType type) {
    }
}
