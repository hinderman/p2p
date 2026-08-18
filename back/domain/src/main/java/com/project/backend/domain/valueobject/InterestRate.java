package com.project.backend.domain.valueobject;

import com.project.backend.domain.exception.DomainRuleViolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Tasa expresada como percentage, por ejemplo 2.50000000 representa 2,5 %. */
public record InterestRate(BigDecimal percentage) implements ValueObject {
    public static final int SCALE = 8;

    public InterestRate {
        Objects.requireNonNull(percentage, "The interest rate is required");
        if (percentage.signum() < 0) {
            throw new DomainRuleViolation("The interest rate cannot be negative");
        }
        try {
            percentage = percentage.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new DomainRuleViolation("The interest rate exceeds the supported precision");
        }
    }
}
