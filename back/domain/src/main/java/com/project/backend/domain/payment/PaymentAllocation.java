package com.project.backend.domain.payment;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ValueObject;

import java.util.Objects;

/** A portion of an approved payment allocated to an obligation or as an unrestricted principal prepayment. */
public record PaymentAllocation(InstallmentId installmentId, PaymentAllocationType type, Money amount) implements ValueObject {
    public PaymentAllocation {
        Objects.requireNonNull(type, "The allocation type is required");
        Objects.requireNonNull(amount, "El amount aplicado es obligatorio");
        if (!amount.isPositive()) {
            throw new DomainRuleViolation("Every payment allocation must be positive");
        }
        if (type == PaymentAllocationType.DIRECT_PRINCIPAL && installmentId != null) {
            throw new DomainRuleViolation("Un abono directo a capital no se asocia a una installment");
        }
        if (type != PaymentAllocationType.DIRECT_PRINCIPAL && installmentId == null) {
            throw new DomainRuleViolation("An allocation to installment interest, charges, or principal requires an installment");
        }
    }
}
