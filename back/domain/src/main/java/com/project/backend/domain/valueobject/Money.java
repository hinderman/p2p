package com.project.backend.domain.valueobject;

import com.project.backend.domain.exception.DomainRuleViolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;

/** Exact monetary amount. The domain never uses {@code double} for money. */
public record Money(BigDecimal amount, String currency) implements ValueObject {

    public static final int SCALE = 4;

    public Money {
        Objects.requireNonNull(amount, "El amount es obligatorio");
        Objects.requireNonNull(currency, "La currency es obligatoria");
        currency = currency.toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            throw new DomainRuleViolation("Currency must use a three-letter ISO code");
        }
        try {
            amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new DomainRuleViolation("Amount cannot have more than %d decimal places".formatted(SCALE));
        }
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "El amount a comparar es obligatorio");
        if (!currency.equals(other.currency)) {
            throw new DomainRuleViolation("No se pueden operar montos de monedas diferentes");
        }
    }
}
