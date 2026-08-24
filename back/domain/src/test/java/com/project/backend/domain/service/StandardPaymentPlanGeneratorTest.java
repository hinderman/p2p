package com.project.backend.domain.service;

import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.Installment;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.NonBusinessDayAdjustment;
import com.project.backend.domain.loan.OutstandingInstallmentBalance;
import com.project.backend.domain.loan.PaymentFrequency;
import com.project.backend.domain.loan.PaymentPlan;
import com.project.backend.domain.loan.PaymentScheduleRule;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.payment.PaymentAllocationType;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.InterestRate;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PaymentPlanId;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardPaymentPlanGeneratorTest {
    private final StandardPaymentPlanGenerator generator = new StandardPaymentPlanGenerator();

    @Test
    void generates_a_fixed_payment_plan_that_settles_principal_exactly() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PAYMENT, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                2, "2.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);

        PaymentPlan plan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));

        assertEquals(2, plan.installments().size());
        assertEquals(money("1000.0000"), totalPrincipal(plan.installments()));
        assertEquals(money("515.0495"), plan.installments().getFirst().total());
        assertEquals(plan.installments().getFirst().total(), plan.installments().get(1).total());
    }

    @Test
    void assigns_rounding_residual_to_the_final_fixed_principal_installment() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                3, "0.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);

        PaymentPlan plan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));

        assertEquals(money("333.3333"), plan.installments().getFirst().agreedPrincipal());
        assertEquals(money("333.3333"), plan.installments().get(1).agreedPrincipal());
        assertEquals(money("333.3334"), plan.installments().get(2).agreedPrincipal());
        assertEquals(money("1000.0000"), totalPrincipal(plan.installments()));
    }

    @Test
    void defers_all_accrued_interest_to_the_maturity_installment_when_requested() {
        LoanTerms terms = terms(AmortizationMethod.INTEREST_AT_MATURITY, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                3, "2.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);

        PaymentPlan plan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));

        assertEquals(money("0.0000"), plan.installments().getFirst().agreedInterest());
        assertEquals(money("0.0000"), plan.installments().get(1).agreedInterest());
        assertEquals(money("40.0000"), plan.installments().get(2).agreedInterest());
    }

    @Test
    void generates_monthly_dates_for_every_configured_payment_day_and_adjusts_weekends() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                3, "0.00000000", LocalDate.of(2026, 3, 15), Set.of(15, 30), NonBusinessDayAdjustment.NEXT_BUSINESS_DAY);

        PaymentPlan plan = generator.generateInitial(planId(), terms, Instant.parse("2026-03-01T12:00:00Z"));

        assertEquals(List.of(
                LocalDate.of(2026, 3, 16),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 4, 15)),
                plan.installments().stream().map(Installment::dueDate).toList());
    }

    @Test
    void shortens_the_term_after_a_direct_principal_prepayment() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.SHORTEN_TERM,
                4, "0.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);
        PaymentPlan initialPlan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));
        Loan loan = activeLoan(terms, initialPlan);

        PaymentPlan replacement = generator.recalculateAfterCapitalPrepayment(
                planId(), loan, approvedPrepayment(loan.id(), "300.0000", LocalDate.of(2026, 1, 15)),
                outstanding(initialPlan),
                Instant.parse("2026-01-15T12:00:00Z"));

        assertEquals(3, replacement.installments().size());
        assertEquals(money("250.0000"), replacement.installments().getFirst().agreedPrincipal());
        assertEquals(money("200.0000"), replacement.installments().get(2).agreedPrincipal());
        assertEquals(money("700.0000"), totalPrincipal(replacement.installments()));
    }

    @Test
    void reduces_each_principal_installment_when_the_policy_keeps_the_term() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                4, "0.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);
        PaymentPlan initialPlan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));
        Loan loan = activeLoan(terms, initialPlan);

        PaymentPlan replacement = generator.recalculateAfterCapitalPrepayment(
                planId(), loan, approvedPrepayment(loan.id(), "300.0000", LocalDate.of(2026, 1, 15)),
                outstanding(initialPlan),
                Instant.parse("2026-01-15T12:00:00Z"));

        assertEquals(4, replacement.installments().size());
        assertEquals(money("175.0000"), replacement.installments().getFirst().agreedPrincipal());
        assertEquals(money("700.0000"), totalPrincipal(replacement.installments()));
        assertTrue(replacement.installments().stream().allMatch(installment -> installment.agreedInterest().equals(money("0.0000"))));
    }

    @Test
    void recalculates_from_unpaid_principal_after_an_installment_was_partially_paid() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                4, "0.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);
        PaymentPlan initialPlan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));
        Loan loan = activeLoan(terms, initialPlan);
        Installment first = initialPlan.installments().getFirst();
        List<OutstandingInstallmentBalance> balances = new java.util.ArrayList<>(outstanding(initialPlan));
        balances.set(0, new OutstandingInstallmentBalance(first.id(), first.number(), first.dueDate(),
                first.agreedPrincipal(), first.agreedInterest(), first.agreedFee(), money("150.0000"),
                first.agreedInterest(), first.agreedFee()));

        PaymentPlan replacement = generator.recalculateAfterCapitalPrepayment(
                planId(), loan, approvedPrepayment(loan.id(), "300.0000", LocalDate.of(2026, 1, 15)), balances,
                Instant.parse("2026-01-15T12:00:00Z"));

        assertEquals(money("600.0000"), totalPrincipal(replacement.installments()));
        assertEquals(money("150.0000"), replacement.installments().getFirst().agreedPrincipal());
    }

    @Test
    void preserves_unpaid_interest_that_was_already_due_when_recalculating() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                2, "2.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);
        PaymentPlan initialPlan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));
        Loan loan = activeLoan(terms, initialPlan);
        Installment first = initialPlan.installments().getFirst();
        List<OutstandingInstallmentBalance> balances = new java.util.ArrayList<>(outstanding(initialPlan));
        balances.set(0, new OutstandingInstallmentBalance(first.id(), first.number(), first.dueDate(),
                first.agreedPrincipal(), first.agreedInterest(), first.agreedFee(), first.agreedPrincipal(),
                money("10.0000"), first.agreedFee()));

        PaymentPlan replacement = generator.recalculateAfterCapitalPrepayment(
                planId(), loan, approvedPrepayment(loan.id(), "100.0000", LocalDate.of(2026, 2, 1)), balances,
                Instant.parse("2026-02-01T12:00:00Z"));

        assertEquals(money("10.0000"), replacement.installments().getFirst().agreedInterest());
    }

    @Test
    void credits_interest_paid_in_advance_instead_of_charging_it_again_after_recalculation() {
        LoanTerms terms = terms(AmortizationMethod.FIXED_PRINCIPAL, CapitalPrepaymentPolicy.REDUCE_PAYMENT,
                2, "2.00000000", LocalDate.of(2026, 2, 1), Set.of(1), NonBusinessDayAdjustment.NO_ADJUSTMENT);
        PaymentPlan initialPlan = generator.generateInitial(planId(), terms, Instant.parse("2026-01-01T12:00:00Z"));
        Loan loan = activeLoan(terms, initialPlan);
        Installment first = initialPlan.installments().getFirst();
        List<OutstandingInstallmentBalance> balances = new java.util.ArrayList<>(outstanding(initialPlan));
        balances.set(0, new OutstandingInstallmentBalance(first.id(), first.number(), first.dueDate(),
                first.agreedPrincipal(), first.agreedInterest(), first.agreedFee(), first.agreedPrincipal(),
                money("15.0000"), first.agreedFee()));

        PaymentPlan withoutInterestCredit = generator.recalculateAfterCapitalPrepayment(
                planId(), loan, approvedPrepayment(loan.id(), "100.0000", LocalDate.of(2026, 1, 15)),
                outstanding(initialPlan), Instant.parse("2026-01-15T12:00:00Z"));
        PaymentPlan replacement = generator.recalculateAfterCapitalPrepayment(
                planId(), loan, approvedPrepayment(loan.id(), "100.0000", LocalDate.of(2026, 1, 15)), balances,
                Instant.parse("2026-01-15T12:00:00Z"));

        assertEquals(money("5.0000"), withoutInterestCredit.installments().getFirst().agreedInterest()
                .subtract(replacement.installments().getFirst().agreedInterest()));
    }

    private static LoanTerms terms(
            AmortizationMethod amortizationMethod,
            CapitalPrepaymentPolicy prepaymentPolicy,
            int installmentCount,
            String rate,
            LocalDate firstDueDate,
            Set<Integer> paymentDays,
            NonBusinessDayAdjustment adjustment) {
        return new LoanTerms(
                new LoanTermId(UUID.randomUUID()), 1, money("1000.0000"), new InterestRate(new BigDecimal(rate)),
                RatePeriod.MONTHLY_NOMINAL, InterestCalculationMethod.SIMPLE, DayCountBasis.THIRTY_360,
                amortizationMethod, prepaymentPolicy, installmentCount, firstDueDate, ZoneId.of("UTC"),
                new PaymentScheduleRule(PaymentFrequency.MONTHLY, null, paymentDays, adjustment), LoanTermStatus.ACCEPTED);
    }

    private static Loan activeLoan(LoanTerms terms, PaymentPlan paymentPlan) {
        return Loan.rehydrate(new LoanId(UUID.randomUUID()), new PersonId(UUID.randomUUID()), new PersonId(UUID.randomUUID()),
                LoanStatus.ACTIVE, List.of(terms), paymentPlan, Instant.parse("2026-01-01T12:00:00Z"));
    }

    private static ReportedPayment approvedPrepayment(LoanId loanId, String amount, LocalDate paymentDate) {
        Money paymentAmount = money(amount);
        return ReportedPayment.rehydrate(new ReportedPaymentId(UUID.randomUUID()), loanId, new PersonId(UUID.randomUUID()),
                new UserAccountId(UUID.randomUUID()), PaymentType.CAPITAL_PREPAYMENT, paymentAmount, paymentDate,
                null, UUID.randomUUID(), ReportedPaymentStatus.APPROVED, Instant.parse("2026-01-15T12:00:00Z"), List.of(),
                List.of(new PaymentAllocation(null, PaymentAllocationType.DIRECT_PRINCIPAL, paymentAmount)),
                paymentAmount, null, null);
    }

    private static Money totalPrincipal(List<Installment> installments) {
        return installments.stream().map(Installment::agreedPrincipal).reduce(Money.zero("COP"), Money::add);
    }

    private static List<OutstandingInstallmentBalance> outstanding(PaymentPlan plan) {
        return plan.installments().stream().map(OutstandingInstallmentBalance::from).toList();
    }

    private static Money money(String value) {
        return new Money(new BigDecimal(value), "COP");
    }

    private static PaymentPlanId planId() {
        return new PaymentPlanId(UUID.randomUUID());
    }
}
