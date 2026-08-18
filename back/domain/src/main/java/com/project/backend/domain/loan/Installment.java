package com.project.backend.domain.loan;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.Objects;

/** Immutable contractual obligation within a payment plan. */
public record Installment(
        InstallmentId id,
        int number,
        LocalDate dueDate,
        Money agreedPrincipal,
        Money agreedInterest,
        Money agreedFee) {

    public Installment {
        Objects.requireNonNull(id, "La installment es obligatoria");
        if (number < 1) {
            throw new DomainRuleViolation("The installment number must be positive");
        }
        Objects.requireNonNull(dueDate, "La fecha de vencimiento es obligatoria");
        Objects.requireNonNull(agreedPrincipal, "El capital pactado es obligatorio");
        Objects.requireNonNull(agreedInterest, "The agreed interest is required");
        Objects.requireNonNull(agreedFee, "El cargo pactado es obligatorio");
        if (agreedPrincipal.isNegative() || agreedInterest.isNegative() || agreedFee.isNegative()) {
            throw new DomainRuleViolation("Los componentes de una installment no pueden ser negativos");
        }
        if (!agreedPrincipal.currency().equals(agreedInterest.currency())
                || !agreedPrincipal.currency().equals(agreedFee.currency())) {
            throw new DomainRuleViolation("Todos los componentes de la installment deben usar la misma currency");
        }
        if (!agreedPrincipal.add(agreedInterest).add(agreedFee).isPositive()) {
            throw new DomainRuleViolation("Una installment debe tener un amount mayor que zero");
        }
    }

    public Money total() {
        return agreedPrincipal.add(agreedInterest).add(agreedFee);
    }
}
