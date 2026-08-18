package com.project.backend.domain.loan;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.PaymentPlanId;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.InterestRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanTest {

    @Test
    void activates_the_loan_only_when_the_payer_accepts_a_consistent_plan() {
        PersonId lender = person();
        PersonId payer = person();
        LoanTerms terms = terms();
        Loan loan = Loan.create(loanId(), lender, payer, terms, Instant.parse("2026-08-18T12:00:00Z"));
        PaymentPlan paymentPlan = paymentPlan(terms.id(), 1, PaymentPlanStatus.CURRENT, PaymentPlanReason.ORIGINAL);

        loan.acceptTerms(
                terms.id(),
                payer,
                account(),
                paymentPlan,
                Instant.parse("2026-08-18T12:01:00Z"));

        assertEquals(LoanStatus.ACTIVE, loan.status());
        assertEquals(LoanTermStatus.ACCEPTED, terms.status());
        assertEquals(paymentPlan.id(), loan.currentPaymentPlan().id());
        assertEquals(2, loan.pullEvents().size());
    }

    @Test
    void rejects_acceptance_from_a_person_other_than_the_payer() {
        LoanTerms terms = terms();
        Loan loan = Loan.create(loanId(), person(), person(), terms, Instant.now());

        assertThrows(DomainRuleViolation.class, () -> loan.acceptTerms(
                terms.id(), person(), account(),
                paymentPlan(terms.id(), 1, PaymentPlanStatus.CURRENT, PaymentPlanReason.ORIGINAL), Instant.now()));
    }

    private static LoanTerms terms() {
        return new LoanTerms(
                new LoanTermId(UUID.randomUUID()), 1,
                dinero("100000.0000"), new InterestRate(new BigDecimal("2.50000000")),
                RatePeriod.MONTHLY_NOMINAL, InterestCalculationMethod.SIMPLE,
                DayCountBasis.THIRTY_360, AmortizationMethod.FIXED_PAYMENT,
                CapitalPrepaymentPolicy.SHORTEN_TERM, 2,
                LocalDate.of(2026, 9, 15), ZoneId.of("America/Bogota"),
                new PaymentScheduleRule(PaymentFrequency.MONTHLY, null, Set.of(15), NonBusinessDayAdjustment.NO_ADJUSTMENT),
                LoanTermStatus.PROPOSED);
    }

    private static PaymentPlan paymentPlan(LoanTermId loanTermId, int version, PaymentPlanStatus status, PaymentPlanReason reason) {
        return new PaymentPlan(
                new PaymentPlanId(UUID.randomUUID()), loanTermId, version, reason,
                List.of(
                        installment(1, LocalDate.of(2026, 9, 15)),
                        installment(2, LocalDate.of(2026, 10, 15))),
                status, Instant.parse("2026-08-18T12:00:00Z"), null);
    }

    private static Installment installment(int number, LocalDate fecha) {
        return new Installment(new InstallmentId(UUID.randomUUID()), number, fecha,
                dinero("50000.0000"), dinero("2500.0000"), Money.zero("COP"));
    }

    private static Money dinero(String value) { return new Money(new BigDecimal(value), "COP"); }
    private static PersonId person() { return new PersonId(UUID.randomUUID()); }
    private static UserAccountId account() { return new UserAccountId(UUID.randomUUID()); }
    private static LoanId loanId() { return new LoanId(UUID.randomUUID()); }
}
