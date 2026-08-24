package com.project.backend.domain.loan;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable snapshot of one current-plan installment before a principal
 * prepayment. It keeps both the contractual and unpaid component amounts so a
 * replacement plan cannot resurrect payments that were already approved.
 */
public record OutstandingInstallmentBalance(
        InstallmentId installmentId,
        int number,
        LocalDate dueDate,
        Money agreedPrincipal,
        Money agreedInterest,
        Money agreedFee,
        Money outstandingPrincipal,
        Money outstandingInterest,
        Money outstandingFee) {

    public OutstandingInstallmentBalance {
        Objects.requireNonNull(installmentId, "The source installment is required");
        if (number < 1) {
            throw new DomainRuleViolation("The installment number must be positive");
        }
        Objects.requireNonNull(dueDate, "The installment due date is required");
        Objects.requireNonNull(agreedPrincipal, "The agreed principal is required");
        Objects.requireNonNull(agreedInterest, "The agreed interest is required");
        Objects.requireNonNull(agreedFee, "The agreed fee is required");
        Objects.requireNonNull(outstandingPrincipal, "The outstanding principal is required");
        Objects.requireNonNull(outstandingInterest, "The outstanding interest is required");
        Objects.requireNonNull(outstandingFee, "The outstanding fee is required");
        requireSameCurrency(agreedPrincipal, agreedInterest, agreedFee, outstandingPrincipal, outstandingInterest, outstandingFee);
        requireNonNegative(outstandingPrincipal, "The outstanding principal cannot be negative");
        requireNonNegative(outstandingInterest, "The outstanding interest cannot be negative");
        requireNonNegative(outstandingFee, "The outstanding fee cannot be negative");
        if (outstandingPrincipal.compareTo(agreedPrincipal) > 0
                || outstandingInterest.compareTo(agreedInterest) > 0
                || outstandingFee.compareTo(agreedFee) > 0) {
            throw new DomainRuleViolation("An outstanding installment component cannot exceed its contractual amount");
        }
        if (!outstandingPrincipal.add(outstandingInterest).add(outstandingFee).isPositive()) {
            throw new DomainRuleViolation("An outstanding installment balance must contain an unpaid component");
        }
    }

    public static OutstandingInstallmentBalance from(Installment installment) {
        Objects.requireNonNull(installment, "The installment is required");
        return new OutstandingInstallmentBalance(installment.id(), installment.number(), installment.dueDate(),
                installment.agreedPrincipal(), installment.agreedInterest(), installment.agreedFee(),
                installment.agreedPrincipal(), installment.agreedInterest(), installment.agreedFee());
    }

    public Money agreedTotal() {
        return agreedPrincipal.add(agreedInterest).add(agreedFee);
    }

    public Money outstandingTotal() {
        return outstandingPrincipal.add(outstandingInterest).add(outstandingFee);
    }

    private static void requireNonNegative(Money amount, String message) {
        if (amount.isNegative()) {
            throw new DomainRuleViolation(message);
        }
    }

    private static void requireSameCurrency(Money first, Money... others) {
        for (Money other : others) {
            if (!first.currency().equals(other.currency())) {
                throw new DomainRuleViolation("All installment balance components must use the same currency");
            }
        }
    }
}
