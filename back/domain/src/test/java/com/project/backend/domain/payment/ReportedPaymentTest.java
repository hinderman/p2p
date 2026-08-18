package com.project.backend.domain.payment;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.LoanId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportedPaymentTest {

    @Test
    void approves_a_payment_when_allocations_equal_the_validated_amount() {
        ReportedPayment payment = payment(PaymentType.INSTALLMENT, dinero("100.0000"));
        payment.attachProof(proof());
        payment.submitForReview(Instant.now());

        payment.approve(account(), dinero("100.0000"), List.of(
                new PaymentAllocation(new InstallmentId(UUID.randomUUID()), PaymentAllocationType.INTEREST, dinero("20.0000")),
                new PaymentAllocation(new InstallmentId(UUID.randomUUID()), PaymentAllocationType.INSTALLMENT_PRINCIPAL, dinero("80.0000"))), Instant.now());

        assertEquals(ReportedPaymentStatus.APPROVED, payment.status());
        assertEquals(2, payment.allocations().size());
    }

    @Test
    void prevents_approval_of_a_payment_with_incomplete_allocation() {
        ReportedPayment payment = payment(PaymentType.INSTALLMENT, dinero("100.0000"));
        payment.attachProof(proof());
        payment.submitForReview(Instant.now());

        assertThrows(DomainRuleViolation.class, () -> payment.approve(
                account(), dinero("100.0000"),
                List.of(new PaymentAllocation(new InstallmentId(UUID.randomUUID()), PaymentAllocationType.INSTALLMENT_PRINCIPAL, dinero("90.0000"))),
                Instant.now()));
    }

    @Test
    void a_principal_prepayment_cannot_be_allocated_to_an_installment() {
        ReportedPayment payment = payment(PaymentType.CAPITAL_PREPAYMENT, dinero("100.0000"));
        payment.attachProof(proof());
        payment.submitForReview(Instant.now());

        assertThrows(DomainRuleViolation.class, () -> payment.approve(
                account(), dinero("100.0000"),
                List.of(new PaymentAllocation(new InstallmentId(UUID.randomUUID()), PaymentAllocationType.INSTALLMENT_PRINCIPAL, dinero("100.0000"))),
                Instant.now()));
    }

    private static ReportedPayment payment(PaymentType type, Money amount) {
        return ReportedPayment.create(
                new ReportedPaymentId(UUID.randomUUID()), new LoanId(UUID.randomUUID()), new PersonId(UUID.randomUUID()),
                account(), type, amount, LocalDate.of(2026, 8, 18), "BANCO-001", Instant.now());
    }

    private static PaymentProof proof() {
        return new PaymentProof(UUID.randomUUID(), "a".repeat(64));
    }

    private static Money dinero(String value) { return new Money(new BigDecimal(value), "COP"); }
    private static UserAccountId account() { return new UserAccountId(UUID.randomUUID()); }
}
