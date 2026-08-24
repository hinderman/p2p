-- Financial-integrity hardening for a database provisioned from .database.
-- Flyway baselines an existing database at version 0, then applies this file.
-- The application database role must treat the two ledger tables as append-only.

CREATE TABLE loans.financial_journals (
    financial_journal_id      UUID PRIMARY KEY,
    loan_id                   UUID NOT NULL,
    reported_payment_id       UUID NOT NULL,
    journal_type              VARCHAR(30) NOT NULL,
    currency_code             VARCHAR(3) NOT NULL,
    recorded_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_financial_journals_loan FOREIGN KEY (loan_id) REFERENCES loans.loans (loan_id),
    CONSTRAINT fk_financial_journals_payment FOREIGN KEY (reported_payment_id)
        REFERENCES loans.reported_payments (reported_payment_id),
    CONSTRAINT uq_financial_journals_payment_type UNIQUE (reported_payment_id, journal_type),
    CONSTRAINT ck_financial_journals_type CHECK (journal_type IN ('PAYMENT_APPROVAL', 'PAYMENT_REVERSAL')),
    CONSTRAINT ck_financial_journals_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE TABLE loans.financial_ledger_entries (
    financial_ledger_entry_id UUID PRIMARY KEY,
    financial_journal_id      UUID NOT NULL,
    account_code              VARCHAR(30) NOT NULL,
    entry_side                VARCHAR(10) NOT NULL,
    amount                    NUMERIC(19, 4) NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_financial_ledger_entries_journal FOREIGN KEY (financial_journal_id)
        REFERENCES loans.financial_journals (financial_journal_id),
    CONSTRAINT uq_financial_ledger_entries_journal_account_side
        UNIQUE (financial_journal_id, account_code, entry_side),
    CONSTRAINT ck_financial_ledger_entries_account
        CHECK (account_code IN ('CASH_CLEARING', 'PRINCIPAL_RECEIVABLE', 'INTEREST_RECEIVABLE', 'FEE_RECEIVABLE')),
    CONSTRAINT ck_financial_ledger_entries_side CHECK (entry_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_financial_ledger_entries_amount CHECK (amount > 0)
);

CREATE INDEX ix_financial_journals_loan_recorded_at
    ON loans.financial_journals (loan_id, recorded_at DESC);

CREATE INDEX ix_financial_ledger_entries_journal
    ON loans.financial_ledger_entries (financial_journal_id);

-- Reconciliation is intentionally queryable rather than silently repaired.
-- A nonzero difference identifies a corrupted journal and should page operations.
CREATE VIEW loans.financial_journal_reconciliation AS
SELECT journal.financial_journal_id,
       journal.loan_id,
       journal.reported_payment_id,
       journal.journal_type,
       journal.currency_code,
       COALESCE(SUM(CASE WHEN entry.entry_side = 'DEBIT' THEN entry.amount ELSE 0 END), 0) AS debits,
       COALESCE(SUM(CASE WHEN entry.entry_side = 'CREDIT' THEN entry.amount ELSE 0 END), 0) AS credits,
       COALESCE(SUM(CASE WHEN entry.entry_side = 'DEBIT' THEN entry.amount ELSE -entry.amount END), 0) AS difference
FROM loans.financial_journals journal
LEFT JOIN loans.financial_ledger_entries entry ON entry.financial_journal_id = journal.financial_journal_id
GROUP BY journal.financial_journal_id, journal.loan_id, journal.reported_payment_id,
         journal.journal_type, journal.currency_code;
