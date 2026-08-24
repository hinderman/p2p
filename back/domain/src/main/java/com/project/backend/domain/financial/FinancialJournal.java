package com.project.backend.domain.financial;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.payment.PaymentAllocationType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, balanced accounting representation of an approved payment or
 * its compensating reversal. The aggregate payment remains the operational
 * record; this object is the append-only financial audit trail.
 */
public record FinancialJournal(
        FinancialJournalType type,
        LoanId loanId,
        ReportedPaymentId reportedPaymentId,
        String currency,
        List<LedgerEntry> entries) {

    public FinancialJournal {
        Objects.requireNonNull(type, "The financial journal type is required");
        Objects.requireNonNull(loanId, "The journal loan is required");
        Objects.requireNonNull(reportedPaymentId, "The journal payment is required");
        Objects.requireNonNull(currency, "The journal currency is required");
        entries = List.copyOf(Objects.requireNonNull(entries, "The journal entries are required"));
        if (entries.size() < 2) {
            throw new DomainRuleViolation("A financial journal requires at least two entries");
        }
        Money debits = Money.zero(currency);
        Money credits = Money.zero(currency);
        for (LedgerEntry entry : entries) {
            if (!currency.equals(entry.amount().currency())) {
                throw new DomainRuleViolation("All journal entries must use the journal currency");
            }
            if (entry.side() == LedgerEntrySide.DEBIT) {
                debits = debits.add(entry.amount());
            } else {
                credits = credits.add(entry.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new DomainRuleViolation("A financial journal must balance debits and credits exactly");
        }
    }

    public static FinancialJournal approvalFor(ReportedPayment payment) {
        Objects.requireNonNull(payment, "The approved payment is required");
        if (payment.status() != com.project.backend.domain.payment.ReportedPaymentStatus.APPROVED) {
            throw new DomainRuleViolation("Only an approved payment can create an approval journal");
        }
        Money amount = Objects.requireNonNull(payment.validatedAmount(), "An approved payment requires a validated amount");
        List<LedgerEntry> entries = new ArrayList<>();
        entries.add(new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.DEBIT, amount));
        allocationTotals(payment.allocations(), amount.currency()).forEach((account, allocatedAmount) ->
                entries.add(new LedgerEntry(account, LedgerEntrySide.CREDIT, allocatedAmount)));
        return new FinancialJournal(FinancialJournalType.PAYMENT_APPROVAL, payment.loanId(), payment.id(), amount.currency(), entries);
    }

    public static FinancialJournal reversalFor(ReportedPayment payment) {
        Objects.requireNonNull(payment, "The reversed payment is required");
        if (payment.status() != com.project.backend.domain.payment.ReportedPaymentStatus.REVERSED) {
            throw new DomainRuleViolation("Only a reversed payment can create a reversal journal");
        }
        Money amount = Objects.requireNonNull(payment.validatedAmount(), "A reversed payment requires a validated amount");
        List<LedgerEntry> entries = new ArrayList<>();
        allocationTotals(payment.allocations(), amount.currency()).forEach((account, allocatedAmount) ->
                entries.add(new LedgerEntry(account, LedgerEntrySide.DEBIT, allocatedAmount)));
        entries.add(new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.CREDIT, amount));
        return new FinancialJournal(FinancialJournalType.PAYMENT_REVERSAL, payment.loanId(), payment.id(), amount.currency(), entries);
    }

    private static Map<LedgerAccount, Money> allocationTotals(List<PaymentAllocation> allocations, String currency) {
        if (allocations.isEmpty()) {
            throw new DomainRuleViolation("A financial journal requires payment allocations");
        }
        Map<LedgerAccount, Money> totals = new EnumMap<>(LedgerAccount.class);
        for (PaymentAllocation allocation : allocations) {
            LedgerAccount account = switch (allocation.type()) {
                case INSTALLMENT_PRINCIPAL, DIRECT_PRINCIPAL -> LedgerAccount.PRINCIPAL_RECEIVABLE;
                case INTEREST -> LedgerAccount.INTEREST_RECEIVABLE;
                case FEE -> LedgerAccount.FEE_RECEIVABLE;
            };
            totals.merge(account, allocation.amount(), Money::add);
        }
        return totals;
    }
}
