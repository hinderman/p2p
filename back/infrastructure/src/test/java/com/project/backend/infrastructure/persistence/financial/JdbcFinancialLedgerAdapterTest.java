package com.project.backend.infrastructure.persistence.financial;

import com.project.backend.domain.financial.FinancialJournal;
import com.project.backend.domain.financial.FinancialJournalType;
import com.project.backend.domain.financial.LedgerAccount;
import com.project.backend.domain.financial.LedgerEntry;
import com.project.backend.domain.financial.LedgerEntrySide;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcFinancialLedgerAdapterTest {

    @Test
    void writes_the_journal_and_each_balanced_entry_in_the_same_transaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        JdbcFinancialLedgerAdapter adapter = new JdbcFinancialLedgerAdapter(jdbcTemplate);

        adapter.record(journal(), Instant.parse("2026-08-20T12:00:00Z"));

        verify(jdbcTemplate, times(3)).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void does_not_duplicate_entries_when_the_payment_journal_already_exists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        JdbcFinancialLedgerAdapter adapter = new JdbcFinancialLedgerAdapter(jdbcTemplate);

        adapter.record(journal(), Instant.parse("2026-08-20T12:00:00Z"));

        verify(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    private static FinancialJournal journal() {
        return new FinancialJournal(FinancialJournalType.PAYMENT_APPROVAL, new LoanId(UUID.randomUUID()),
                new ReportedPaymentId(UUID.randomUUID()), "COP", List.of(
                        new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.DEBIT, money("100.0000")),
                        new LedgerEntry(LedgerAccount.PRINCIPAL_RECEIVABLE, LedgerEntrySide.CREDIT, money("100.0000"))));
    }

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount), "COP");
    }
}
