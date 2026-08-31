package com.project.backend.api.dto.response;

import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.RatePeriod;

import java.time.LocalDate;

public record LoanTermsDetailResponse(
        int versionNumber,
        String interestRatePercentage,
        RatePeriod ratePeriod,
        InterestCalculationMethod interestCalculationMethod,
        DayCountBasis dayCountBasis,
        AmortizationMethod amortizationMethod,
        CapitalPrepaymentPolicy capitalPrepaymentPolicy,
        int installmentCount,
        LocalDate firstDueDate,
        String timeZone) { }
