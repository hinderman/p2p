package com.project.backend.domain.loan;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.ValueObject;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Declarative rule; due-date calculation will be implemented as a domain service. */
public record PaymentScheduleRule(
        PaymentFrequency frequency,
        Integer intervalDays,
        Set<Integer> daysOfMonth,
        NonBusinessDayAdjustment nonBusinessDayAdjustment) implements ValueObject {

    public PaymentScheduleRule {
        Objects.requireNonNull(frequency, "La frequency de payment es obligatoria");
        Objects.requireNonNull(daysOfMonth, "Payment days are required");
        Objects.requireNonNull(nonBusinessDayAdjustment, "The non-business-day adjustment policy is required");
        if (daysOfMonth.stream().anyMatch(day -> day == null || day < 1 || day > 31)) {
            throw new DomainRuleViolation("Payment days must be between 1 and 31");
        }
        daysOfMonth = Set.copyOf(new LinkedHashSet<>(daysOfMonth));
        if (frequency == PaymentFrequency.EVERY_N_DAYS) {
            if (intervalDays == null || intervalDays <= 0 || !daysOfMonth.isEmpty()) {
                throw new DomainRuleViolation("EVERY_N_DAYS frequency requires a positive interval and no days of month");
            }
        } else {
            if (intervalDays != null) {
                throw new DomainRuleViolation("Only EVERY_N_DAYS can define a day interval");
            }
            if (frequency == PaymentFrequency.MONTHLY && daysOfMonth.isEmpty()) {
                throw new DomainRuleViolation("MONTHLY frequency requires at least one payment day");
            }
        }
    }
}
