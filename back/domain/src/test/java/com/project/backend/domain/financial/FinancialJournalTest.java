package com.project.backend.domain.financial;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.payment.PaymentAllocationType;
import com.project.backend.domain.payment.PaymentProof;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinancialJournalTest {

    @Test
    void approval_and_reversal_create_exact_compensating_balanced_journals() {
        ReportedPayment payment = approvedPayment();

        FinancialJournal approval = FinancialJournal.approvalFor(payment);
        payment.reverse(new UserAccountId(UUID.randomUUID()), "Bank transfer was reversed", Instant.parse("2026-08-20T12:00:00Z"));
        FinancialJournal reversal = FinancialJournal.reversalFor(payment);

        assertEquals(FinancialJournalType.PAYMENT_APPROVAL, approval.type());
        assertEquals(List.of(
                new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.DEBIT, money("100.0000")),
                new LedgerEntry(LedgerAccount.PRINCIPAL_RECEIVABLE, LedgerEntrySide.CREDIT, money("80.0000")),
                new LedgerEntry(LedgerAccount.INTEREST_RECEIVABLE, LedgerEntrySide.CREDIT, money("20.0000"))), approval.entries());
        assertEquals(List.of(
                new LedgerEntry(LedgerAccount.PRINCIPAL_RECEIVABLE, LedgerEntrySide.DEBIT, money("80.0000")),
                new LedgerEntry(LedgerAccount.INTEREST_RECEIVABLE, LedgerEntrySide.DEBIT, money("20.0000")),
                new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.CREDIT, money("100.0000"))), reversal.entries());
    }

    @Test
    void rejects_an_unbalanced_journal() {
        assertThrows(DomainRuleViolation.class, () -> new FinancialJournal(
                FinancialJournalType.PAYMENT_APPROVAL, new LoanId(UUID.randomUUID()), new ReportedPaymentId(UUID.randomUUID()),
                "COP", List.of(
                        new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.DEBIT, money("100.0000")),
                        new LedgerEntry(LedgerAccount.PRINCIPAL_RECEIVABLE, LedgerEntrySide.CREDIT, money("99.0000")))));
    }

    private static ReportedPayment approvedPayment() {
        UserAccountId reporter = new UserAccountId(UUID.randomUUID());
        ReportedPayment payment = ReportedPayment.create(new ReportedPaymentId(UUID.randomUUID()), new LoanId(UUID.randomUUID()),
                new PersonId(UUID.randomUUID()), reporter, PaymentType.INSTALLMENT, money("100.0000"),
                LocalDate.of(2026, 8, 20), "TRANSFER-1", UUID.randomUUID(), Instant.parse("2026-08-20T10:00:00Z"));
        payment.attachProof(new PaymentProof(UUID.randomUUID(), "a".repeat(64)));
        payment.submitForReview(Instant.parse("2026-08-20T10:01:00Z"));
        payment.approve(new UserAccountId(UUID.randomUUID()), money("100.0000"), List.of(
                new PaymentAllocation(new InstallmentId(UUID.randomUUID()), PaymentAllocationType.INTEREST, money("20.0000")),
                new PaymentAllocation(new InstallmentId(UUID.randomUUID()), PaymentAllocationType.INSTALLMENT_PRINCIPAL, money("80.0000"))),
                Instant.parse("2026-08-20T11:00:00Z"));
        return payment;
    }

    private static Money money(String value) {
        return new Money(new BigDecimal(value), "COP");
    }
}
