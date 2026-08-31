package com.project.backend.application.dto;

import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.RatePeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record LoanTermsDetail(
        int versionNumber,
        BigDecimal interestRatePercentage,
        RatePeriod ratePeriod,
        InterestCalculationMethod interestCalculationMethod,
        DayCountBasis dayCountBasis,
        AmortizationMethod amortizationMethod,
        CapitalPrepaymentPolicy capitalPrepaymentPolicy,
        int installmentCount,
        LocalDate firstDueDate,
        String timeZone) {
    public LoanTermsDetail {
        Objects.requireNonNull(interestRatePercentage, "The interest rate is required");
        Objects.requireNonNull(ratePeriod, "The rate period is required");
        Objects.requireNonNull(interestCalculationMethod, "The interest method is required");
        Objects.requireNonNull(dayCountBasis, "The day count basis is required");
        Objects.requireNonNull(amortizationMethod, "The amortization method is required");
        Objects.requireNonNull(capitalPrepaymentPolicy, "The prepayment policy is required");
        Objects.requireNonNull(firstDueDate, "The first due date is required");
        Objects.requireNonNull(timeZone, "The time zone is required");
    }
}
