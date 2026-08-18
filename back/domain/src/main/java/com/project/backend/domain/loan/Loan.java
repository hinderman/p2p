package com.project.backend.domain.loan;

import com.project.backend.domain.entity.AggregateRoot;
import com.project.backend.domain.event.PaymentPlanRecalculated;
import com.project.backend.domain.event.LoanActivated;
import com.project.backend.domain.event.LoanCreated;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Primary loan aggregate. It protects term acceptance and
 * auditable plan replacement, without knowing HTTP, persistence, or Spring users.
 */
public final class Loan extends AggregateRoot<LoanId> {
    private final PersonId lenderPersonId;
    private final PersonId payerPersonId;
    private final Instant createdAt;
    private LoanStatus status;
    private final List<LoanTerms> terms;
    private PaymentPlan currentPaymentPlan;

    private Loan(
            LoanId id,
            PersonId lenderPersonId,
            PersonId payerPersonId,
            LoanStatus status,
            List<LoanTerms> terms,
            PaymentPlan currentPaymentPlan,
            Instant createdAt) {
        super(id);
        this.lenderPersonId = Objects.requireNonNull(lenderPersonId, "The lender is required");
        this.payerPersonId = Objects.requireNonNull(payerPersonId, "The payer is required");
        if (lenderPersonId.equals(payerPersonId)) {
            throw new DomainRuleViolation("The lender and payer must be different people");
        }
        this.status = Objects.requireNonNull(status, "The loan status is required");
        this.terms = new ArrayList<>(Objects.requireNonNull(terms, "Loan terms are required"));
        if (this.terms.isEmpty()) {
            throw new DomainRuleViolation("Every loan must have terms");
        }
        this.currentPaymentPlan = currentPaymentPlan;
        this.createdAt = Objects.requireNonNull(createdAt, "The creation time is required");
        validateConsistency();
    }

    public static Loan create(
            LoanId id,
            PersonId lenderPersonId,
            PersonId payerPersonId,
            LoanTerms initialTerms,
            Instant createdAt) {
        Objects.requireNonNull(initialTerms, "The initial loan terms are required");
        if (initialTerms.status() != LoanTermStatus.PROPOSED || initialTerms.versionNumber() != 1) {
            throw new DomainRuleViolation("A new loan requires an initial proposal at version one");
        }
        Loan loan = new Loan(
                id,
                lenderPersonId,
                payerPersonId,
                LoanStatus.PENDING_ACCEPTANCE,
                List.of(initialTerms),
                null,
                createdAt);
        loan.registerEvent(new LoanCreated(id, initialTerms.id(), createdAt));
        return loan;
    }

    /** Rehydration constructor for the persistence adapter. */
    public static Loan rehydrate(
            LoanId id,
            PersonId lenderPersonId,
            PersonId payerPersonId,
            LoanStatus status,
            List<LoanTerms> terms,
            PaymentPlan currentPaymentPlan,
            Instant createdAt) {
        return new Loan(id, lenderPersonId, payerPersonId, status, terms, currentPaymentPlan, createdAt);
    }

    public PersonId lenderPersonId() { return lenderPersonId; }
    public PersonId payerPersonId() { return payerPersonId; }
    public LoanStatus status() { return status; }
    public List<LoanTerms> terms() { return List.copyOf(terms); }
    public PaymentPlan currentPaymentPlan() { return currentPaymentPlan; }
    public Instant createdAt() { return createdAt; }

    public void acceptTerms(
            LoanTermId loanTermId,
            PersonId acceptingPerson,
            UserAccountId acceptingAccountId,
            PaymentPlan initialPaymentPlan,
            Instant acceptedAt) {
        Objects.requireNonNull(loanTermId, "The loan terms to accept are required");
        Objects.requireNonNull(acceptingPerson, "The accepting person is required");
        Objects.requireNonNull(acceptingAccountId, "The accepting account is required");
        Objects.requireNonNull(initialPaymentPlan, "The initial payment plan is required");
        Objects.requireNonNull(acceptedAt, "The acceptance time is required");
        if (status != LoanStatus.PENDING_ACCEPTANCE) {
            throw new DomainRuleViolation("The loan is not awaiting acceptance");
        }
        if (!payerPersonId.equals(acceptingPerson)) {
            throw new DomainRuleViolation("Only the payer may accept the loan");
        }
        LoanTerms term = findTerms(loanTermId);
        if (!initialPaymentPlan.loanTermId().equals(term.id())
                || initialPaymentPlan.status() != PaymentPlanStatus.CURRENT
                || initialPaymentPlan.versionNumber() != 1
                || initialPaymentPlan.installments().size() != term.installmentCount()) {
            throw new DomainRuleViolation("The initial payment plan does not correspond to the accepted terms");
        }
        if (!initialPaymentPlan.installments().getFirst().agreedPrincipal().currency().equals(term.originalPrincipal().currency())) {
            throw new DomainRuleViolation("The payment plan and loan must use the same currency");
        }
        term.accept();
        currentPaymentPlan = initialPaymentPlan;
        status = LoanStatus.ACTIVE;
        registerEvent(new LoanActivated(id(), term.id(), initialPaymentPlan.id(), acceptedAt));
    }

    public void replacePlanAfterCapitalPrepayment(PaymentPlan newPaymentPlan, Instant occurredAt) {
        Objects.requireNonNull(newPaymentPlan, "The new payment plan is required");
        Objects.requireNonNull(occurredAt, "The recalculation time is required");
        if (status != LoanStatus.ACTIVE || currentPaymentPlan == null) {
            throw new DomainRuleViolation("Only an active loan with a current payment plan may be recalculated");
        }
        LoanTerms term = acceptedTerms();
        if (term.capitalPrepaymentPolicy() == CapitalPrepaymentPolicy.NO_RECALCULATION) {
            throw new DomainRuleViolation("The loan terms do not permit payment-plan recalculation after a principal prepayment");
        }
        if (!newPaymentPlan.loanTermId().equals(term.id())
                || newPaymentPlan.status() != PaymentPlanStatus.CURRENT
                || newPaymentPlan.versionNumber() != currentPaymentPlan.versionNumber() + 1
                || newPaymentPlan.reason() != PaymentPlanReason.CAPITAL_PREPAYMENT) {
            throw new DomainRuleViolation("The new payment plan is not a valid continuation of the loan");
        }
        PaymentPlan previous = currentPaymentPlan;
        previous.supersede(occurredAt);
        currentPaymentPlan = newPaymentPlan;
        registerEvent(new PaymentPlanRecalculated(id(), previous.id(), newPaymentPlan.id(), occurredAt));
    }

    public void cancel(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "The cancellation time is required");
        if (status != LoanStatus.PENDING_ACCEPTANCE && status != LoanStatus.DRAFT) {
            throw new DomainRuleViolation("Only a loan that has not been activated may be cancelled");
        }
        status = LoanStatus.CANCELLED;
    }

    private LoanTerms findTerms(LoanTermId loanTermId) {
        return terms.stream()
                .filter(term -> term.id().equals(loanTermId))
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("The loan terms do not belong to this loan"));
    }

    private LoanTerms acceptedTerms() {
        return terms.stream()
                .filter(term -> term.status() == LoanTermStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("An active loan must have accepted terms"));
    }

    private void validateConsistency() {
        long acceptedCount = terms.stream()
                .filter(term -> term.status() == LoanTermStatus.ACCEPTED)
                .count();
        if (acceptedCount > 1) {
            throw new DomainRuleViolation("A loan cannot have more than one accepted term proposal");
        }
        if (status == LoanStatus.ACTIVE && (acceptedCount != 1 || currentPaymentPlan == null)) {
            throw new DomainRuleViolation("An active loan requires current terms and a payment plan");
        }
        if (status != LoanStatus.ACTIVE && currentPaymentPlan != null) {
            throw new DomainRuleViolation("Only an active loan may have a current payment plan");
        }
    }
}
