package com.project.backend.infrastructure.persistence.financial;

import com.project.backend.application.port.out.FinancialLedgerPort;
import com.project.backend.domain.financial.FinancialJournal;
import com.project.backend.domain.financial.LedgerEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** PostgreSQL append-only journal writer. Its unique key makes command retries harmless. */
@Component
public final class JdbcFinancialLedgerAdapter implements FinancialLedgerPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcFinancialLedgerAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(FinancialJournal journal, Instant recordedAt) {
        UUID journalId = UUID.randomUUID();
        int inserted = jdbcTemplate.update("""
                INSERT INTO loans.financial_journals
                (financial_journal_id, loan_id, reported_payment_id, journal_type, currency_code, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (reported_payment_id, journal_type) DO NOTHING
                """, journalId, journal.loanId().value(), journal.reportedPaymentId().value(), journal.type().name(),
                journal.currency(), recordedAt);
        if (inserted == 0) {
            return;
        }
        for (LedgerEntry entry : journal.entries()) {
            jdbcTemplate.update("""
                    INSERT INTO loans.financial_ledger_entries
                    (financial_ledger_entry_id, financial_journal_id, account_code, entry_side, amount, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), journalId, entry.account().name(), entry.side().name(),
                    entry.amount().amount(), recordedAt);
        }
    }
}
