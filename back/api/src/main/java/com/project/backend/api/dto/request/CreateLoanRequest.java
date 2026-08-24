package com.project.backend.api.dto.request;

import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.RatePeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lender proposal input; the authenticated lender is never supplied by the client. */
public record CreateLoanRequest(
        @NotBlank @Email @Size(max = 254) String payerEmail,
        @NotNull @Valid MoneyRequest originalPrincipal,
        @NotNull @DecimalMin(value = "0.0") @Digits(integer = 4, fraction = 8) BigDecimal interestRatePercentage,
        @NotNull RatePeriod ratePeriod,
        @NotNull InterestCalculationMethod interestCalculationMethod,
        @NotNull DayCountBasis dayCountBasis,
        @NotNull AmortizationMethod amortizationMethod,
        @NotNull CapitalPrepaymentPolicy capitalPrepaymentPolicy,
        @Min(1) @Max(600) int installmentCount,
        @NotNull @FutureOrPresent LocalDate firstDueDate,
        @NotBlank @Size(max = 64) String timeZone,
        @NotNull @Valid PaymentScheduleRequest paymentSchedule) {
}
