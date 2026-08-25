package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.LoanCreated;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.PaymentScheduleRule;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.InterestRate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Lender intent to create a loan proposal. */
public record CreateLoanCommand(
        UserAccountId lenderAccountId,
        EmailAddress payerEmail,
        Money originalPrincipal,
        InterestRate interestRate,
        RatePeriod ratePeriod,
        InterestCalculationMethod interestCalculationMethod,
        DayCountBasis dayCountBasis,
        AmortizationMethod amortizationMethod,
        CapitalPrepaymentPolicy capitalPrepaymentPolicy,
        int installmentCount,
        LocalDate firstDueDate,
        ZoneId timeZone,
        PaymentScheduleRule paymentScheduleRule) implements Command<LoanCreated> {
    public CreateLoanCommand {
        Objects.requireNonNull(lenderAccountId, "La account lender es obligatoria");
        Objects.requireNonNull(payerEmail, "El email payer es obligatorio");
        Objects.requireNonNull(originalPrincipal, "El principal es obligatorio");
        Objects.requireNonNull(interestRate, "La tasa es obligatoria");
        Objects.requireNonNull(ratePeriod, "La periodicidad es obligatoria");
        Objects.requireNonNull(interestCalculationMethod, "The interest calculation method is required");
        Objects.requireNonNull(dayCountBasis, "The day-count basis is required");
        Objects.requireNonNull(amortizationMethod, "The amortization method is required");
        Objects.requireNonNull(capitalPrepaymentPolicy, "The principal prepayment policy is required");
        Objects.requireNonNull(firstDueDate, "La primera fecha es obligatoria");
        Objects.requireNonNull(timeZone, "La zona horaria es obligatoria");
        Objects.requireNonNull(paymentScheduleRule, "La regla de payment es obligatoria");
    }
}
