package com.project.backend.api.dto.request;

import com.project.backend.domain.loan.NonBusinessDayAdjustment;
import com.project.backend.domain.loan.PaymentFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Declares the contractual cadence; domain validation enforces frequency-specific combinations. */
public record PaymentScheduleRequest(
        @NotNull PaymentFrequency frequency,
        @Min(1) Integer intervalDays,
        Set<@NotNull @Min(1) @Max(31) Integer> daysOfMonth,
        @NotNull NonBusinessDayAdjustment nonBusinessDayAdjustment) {
}
