package com.project.backend.application.port.out;

import com.project.backend.domain.financial.FinancialJournal;

import java.time.Instant;

/** Appends a balanced, immutable journal in the same transaction as the payment state change. */
public interface FinancialLedgerPort {
    void record(FinancialJournal journal, Instant recordedAt);
}
