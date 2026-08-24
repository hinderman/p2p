package com.project.backend.domain.financial;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.Money;

import java.util.Objects;

/** One immutable line in a balanced financial journal. */
public record LedgerEntry(LedgerAccount account, LedgerEntrySide side, Money amount) {
    public LedgerEntry {
        Objects.requireNonNull(account, "The ledger account is required");
        Objects.requireNonNull(side, "The ledger entry side is required");
        Objects.requireNonNull(amount, "The ledger entry amount is required");
        if (!amount.isPositive()) {
            throw new DomainRuleViolation("A ledger entry amount must be positive");
        }
    }
}
