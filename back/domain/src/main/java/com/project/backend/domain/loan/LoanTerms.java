package com.project.backend.domain.loan;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.InterestRate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Versioned loan terms that can be accepted. */
public final class LoanTerms {
    private final LoanTermId id;
    private final int versionNumber;
    private final Money originalPrincipal;
    private final InterestRate interestRate;
    private final RatePeriod ratePeriod;
    private final InterestCalculationMethod interestCalculationMethod;
    private final DayCountBasis dayCountBasis;
    private final AmortizationMethod amortizationMethod;
    private final CapitalPrepaymentPolicy capitalPrepaymentPolicy;
    private final int installmentCount;
    private final LocalDate firstDueDate;
    private final ZoneId timeZone;
    private final PaymentScheduleRule paymentScheduleRule;
    private LoanTermStatus status;

    public LoanTerms(
            LoanTermId id,
            int versionNumber,
            Money originalPrincipal,
            InterestRate interestRate,
            RatePeriod ratePeriod,
            InterestCalculationMethod interestCalculationMethod,
            DayCountBasis dayCountBasis,
            AmortizationMethod amortizationMethod,
            CapitalPrepaymentPolicy capitalPrepaymentPolicy,
            int installmentCount,
            LocalDate firstDueDate,
            ZoneId timeZone,
            PaymentScheduleRule paymentScheduleRule,
            LoanTermStatus status) {
        this.id = Objects.requireNonNull(id, "Loan terms are required");
        if (versionNumber < 1) {
            throw new DomainRuleViolation("The loan term version must start at one");
        }
        this.versionNumber = versionNumber;
        this.originalPrincipal = Objects.requireNonNull(originalPrincipal, "El principal es obligatorio");
        if (!originalPrincipal.isPositive()) {
            throw new DomainRuleViolation("The loan principal must be positive");
        }
        this.interestRate = Objects.requireNonNull(interestRate, "La tasa es obligatoria");
        this.ratePeriod = Objects.requireNonNull(ratePeriod, "La periodicidad de tasa es obligatoria");
        this.interestCalculationMethod = Objects.requireNonNull(interestCalculationMethod, "The interest calculation method is required");
        this.dayCountBasis = Objects.requireNonNull(dayCountBasis, "The day-count basis is required");
        this.amortizationMethod = Objects.requireNonNull(amortizationMethod, "The amortization method is required");
        this.capitalPrepaymentPolicy = Objects.requireNonNull(capitalPrepaymentPolicy, "The principal prepayment policy is required");
        if (installmentCount < 1) {
            throw new DomainRuleViolation("The loan must have at least one installment");
        }
        this.installmentCount = installmentCount;
        this.firstDueDate = Objects.requireNonNull(firstDueDate, "La primera fecha de vencimiento es obligatoria");
        this.timeZone = Objects.requireNonNull(timeZone, "La zona horaria es obligatoria");
        this.paymentScheduleRule = Objects.requireNonNull(paymentScheduleRule, "La regla de calendario es obligatoria");
        this.status = Objects.requireNonNull(status, "El status de las terms es obligatorio");
    }

    public LoanTermId id() { return id; }
    public int versionNumber() { return versionNumber; }
    public Money originalPrincipal() { return originalPrincipal; }
    public InterestRate interestRate() { return interestRate; }
    public RatePeriod ratePeriod() { return ratePeriod; }
    public InterestCalculationMethod interestCalculationMethod() { return interestCalculationMethod; }
    public DayCountBasis dayCountBasis() { return dayCountBasis; }
    public AmortizationMethod amortizationMethod() { return amortizationMethod; }
    public CapitalPrepaymentPolicy capitalPrepaymentPolicy() { return capitalPrepaymentPolicy; }
    public int installmentCount() { return installmentCount; }
    public LocalDate firstDueDate() { return firstDueDate; }
    public ZoneId timeZone() { return timeZone; }
    public PaymentScheduleRule paymentScheduleRule() { return paymentScheduleRule; }
    public LoanTermStatus status() { return status; }

    void accept() {
        if (status != LoanTermStatus.PROPOSED) {
            throw new DomainRuleViolation("Only a proposed term set can be accepted");
        }
        status = LoanTermStatus.ACCEPTED;
    }

    void supersede() {
        if (status != LoanTermStatus.ACCEPTED) {
            throw new DomainRuleViolation("Solo terms acceptedCount pueden reemplazarse");
        }
        status = LoanTermStatus.SUPERSEDED;
    }

    void cancel() {
        if (status == LoanTermStatus.ACCEPTED) {
            throw new DomainRuleViolation("Accepted terms cannot be cancelled; they must be replaced or the loan must be cancelled");
        }
        status = LoanTermStatus.CANCELLED;
    }
}
