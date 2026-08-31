package com.project.backend.infrastructure.persistence;

import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.dto.PageRequest;
import com.project.backend.application.dto.StoredObjectScanStatus;
import com.project.backend.application.dto.StoredObjectUpload;
import com.project.backend.domain.financial.FinancialJournal;
import com.project.backend.domain.financial.FinancialJournalType;
import com.project.backend.domain.financial.LedgerAccount;
import com.project.backend.domain.financial.LedgerEntry;
import com.project.backend.domain.financial.LedgerEntrySide;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.infrastructure.notification.OutboundEmailPayloadCipher;
import com.project.backend.infrastructure.notification.OutboundEmailProperties;
import com.project.backend.infrastructure.persistence.financial.JdbcFinancialLedgerAdapter;
import com.project.backend.infrastructure.persistence.outbox.JdbcOutboxEventsAdapter;
import com.project.backend.infrastructure.persistence.query.JdbcLoanReadModelAdapter;
import com.project.backend.infrastructure.persistence.query.JdbcPaymentReadModelAdapter;
import com.project.backend.infrastructure.storage.LocalStoredObjectAdapter;
import com.project.backend.infrastructure.storage.PaymentProofStorageProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executes production SQL against PostgreSQL 18, never against an in-memory substitute. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgreSqlPersistenceIT {
    @TempDir
    Path storageRoot;
    private EphemeralPostgreSql database;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private TransactionStatus transaction;

    @BeforeAll
    void provisionDisposableDatabase() {
        database = EphemeralPostgreSql.start();
        jdbc = new JdbcTemplate(database.dataSource());
        transactions = new DataSourceTransactionManager(database.dataSource());
    }

    @BeforeEach
    void beginRollbackOnlyTestTransaction() {
        transaction = transactions.getTransaction(new DefaultTransactionDefinition());
    }

    @AfterEach
    void rollbackAndProveThatNoBusinessRecordsRemain() {
        if (transaction != null && !transaction.isCompleted()) {
            transactions.rollback(transaction);
        }
        transaction = null;
        for (String table : List.of(
                "financial_ledger_entries", "financial_journals", "outbox_events", "payment_reviews",
                "payment_allocations", "reported_payments", "loan_terms", "loans", "user_accounts", "people")) {
            Long remaining = jdbc.queryForObject("SELECT COUNT(*) FROM loans." + table, Long.class);
            assertEquals(0L, remaining, "Integration test records leaked into loans." + table);
        }
    }

    @AfterAll
    void destroyDisposableDatabase() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void provisions_the_database_first_schema_reference_data_and_flyway_migration() {
        int serverVersion = Integer.parseInt(jdbc.queryForObject("SHOW server_version_num", String.class));
        assertTrue(serverVersion >= 180000, "The integration suite requires PostgreSQL 18 or newer");
        assertEquals("loans.loans", jdbc.queryForObject("SELECT to_regclass('loans.loans')::text", String.class));
        assertEquals("loans.financial_journals",
                jdbc.queryForObject("SELECT to_regclass('loans.financial_journals')::text", String.class));
        assertEquals("loans.financial_journal_reconciliation",
                jdbc.queryForObject("SELECT to_regclass('loans.financial_journal_reconciliation')::text", String.class));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM loans.roles", Long.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT success FROM public.flyway_schema_history WHERE version = '1'", Boolean.class));
    }

    @Test
    void stages_and_releases_a_safe_payment_proof_with_real_postgresql_metadata() throws Exception {
        Fixture fixture = insertLoanFixture();
        UUID objectId = UUID.randomUUID();
        byte[] content = "%PDF-integration-proof".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        LocalStoredObjectAdapter adapter = new LocalStoredObjectAdapter(jdbc,
                new PaymentProofStorageProperties(storageRoot));

        adapter.stage(new StoredObjectUpload(objectId,
                new com.project.backend.domain.valueobject.UserAccountId(fixture.payerAccountId()),
                "comprobante.pdf", "application/pdf", content, "a".repeat(64), fixture.createdAt()));
        assertEquals(StoredObjectScanStatus.PENDING,
                adapter.findByIdForUpdate(objectId).orElseThrow().scanStatus());

        adapter.markSafe(objectId);

        var stored = adapter.findByIdForUpdate(objectId).orElseThrow();
        assertEquals(StoredObjectScanStatus.SAFE, stored.scanStatus());
        assertEquals(fixture.payerAccountId(), stored.uploadedBy().value());
        assertFalse(stored.attached());
        assertTrue(Files.exists(storageRoot.resolve(
                "payment-proofs/" + objectId.toString().substring(0, 2) + "/" + objectId)));
    }

    @Test
    void writes_an_idempotent_balanced_journal_and_reconciles_to_zero() {
        Fixture fixture = insertLoanFixture();
        UUID paymentId = insertPayment(fixture, ReportedPaymentStatus.APPROVED, "100.0000",
                Instant.parse("2026-08-20T12:15:00Z"));
        FinancialJournal journal = new FinancialJournal(FinancialJournalType.PAYMENT_APPROVAL,
                new LoanId(fixture.loanId()), new ReportedPaymentId(paymentId), "COP", List.of(
                new LedgerEntry(LedgerAccount.CASH_CLEARING, LedgerEntrySide.DEBIT, money("100.0000")),
                new LedgerEntry(LedgerAccount.PRINCIPAL_RECEIVABLE, LedgerEntrySide.CREDIT, money("100.0000"))));
        JdbcFinancialLedgerAdapter adapter = new JdbcFinancialLedgerAdapter(jdbc);

        adapter.record(journal, Instant.parse("2026-08-20T12:16:00Z"));
        adapter.record(journal, Instant.parse("2026-08-20T12:17:00Z"));

        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM loans.financial_journals", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM loans.financial_ledger_entries", Long.class));
        BigDecimal difference = jdbc.queryForObject(
                "SELECT difference FROM loans.financial_journal_reconciliation WHERE reported_payment_id = ?",
                BigDecimal.class, paymentId);
        assertNotNull(difference);
        assertEquals(0, difference.compareTo(BigDecimal.ZERO));
    }

    @Test
    void stores_an_encrypted_invitation_outbox_record_using_a_real_jsonb_and_timestamptz_column() {
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(invitationProperties());
        JdbcOutboxEventsAdapter adapter = new JdbcOutboxEventsAdapter(jdbc, cipher);
        UUID invitationId = UUID.randomUUID();
        String rawToken = "single-use-integration-token";
        String email = "payer@integration.test";
        Instant occurredAt = Instant.parse("2026-08-20T13:00:00Z");

        adapter.enqueueEmail(OutboundEmailMessage.loanInvitation(
                new LoanInvitationId(invitationId), new EmailAddress(email), rawToken), occurredAt);

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM loans.outbox_events WHERE aggregate_id = ?", String.class, invitationId);
        assertNotNull(payload);
        assertFalse(payload.contains(rawToken));
        assertFalse(payload.contains(email));
        OutboundEmailMessage decrypted = cipher.decrypt(payload);
        assertEquals(invitationId, decrypted.referenceId());
        assertEquals(email, decrypted.recipientEmail().value());
        assertEquals(rawToken, decrypted.rawToken());
        Timestamp persistedAt = jdbc.queryForObject(
                "SELECT occurred_at FROM loans.outbox_events WHERE aggregate_id = ?", Timestamp.class, invitationId);
        assertNotNull(persistedAt);
        assertEquals(occurredAt, persistedAt.toInstant());
    }

    @Test
    void executes_the_loan_projection_and_maps_postgresql_timestamptz_without_loading_errors() {
        Fixture fixture = insertLoanFixture();
        UUID paymentId = insertPayment(fixture, ReportedPaymentStatus.APPROVED, "100.0000",
                Instant.parse("2026-08-20T12:15:00Z"));
        jdbc.update("""
                INSERT INTO loans.payment_allocations
                (payment_allocation_id, reported_payment_id, installment_id, allocation_type, allocated_amount, created_at)
                VALUES (?, ?, NULL, 'DIRECT_PRINCIPAL', 100.0000, ?)
                """, UUID.randomUUID(), paymentId, timestamp("2026-08-20T12:16:00Z"));

        var summaries = new JdbcLoanReadModelAdapter(jdbc).findByLender(new PersonId(fixture.lenderPersonId()));

        assertEquals(1, summaries.size());
        var summary = summaries.getFirst();
        assertEquals(fixture.loanId(), summary.loanId().value());
        assertEquals(fixture.payerPersonId(), summary.counterpartyPersonId().value());
        assertEquals(new BigDecimal("1000.0000"), summary.originalPrincipal().amount());
        assertEquals(new BigDecimal("900.0000"), summary.outstandingBalance().amount());
        assertEquals(fixture.createdAt(), summary.createdAt());
    }

    @Test
    void executes_payment_count_lateral_join_and_limit_offset_in_postgresql() {
        Fixture fixture = insertLoanFixture();
        UUID first = insertPayment(fixture, ReportedPaymentStatus.PENDING_REVIEW, "80.0000",
                Instant.parse("2026-08-20T12:10:00Z"));
        UUID second = insertPayment(fixture, ReportedPaymentStatus.PENDING_REVIEW, "90.0000",
                Instant.parse("2026-08-20T12:11:00Z"));
        UUID third = insertPayment(fixture, ReportedPaymentStatus.PENDING_REVIEW, "100.0000",
                Instant.parse("2026-08-20T12:12:00Z"));
        jdbc.update("""
                INSERT INTO loans.payment_reviews
                (payment_review_id, reported_payment_id, review_number, reviewer_user_account_id,
                 decision, validated_amount, reason, reviewed_at)
                VALUES (?, ?, 1, ?, 'APPROVED', 95.0000, NULL, ?)
                """, UUID.randomUUID(), third, fixture.lenderAccountId(), timestamp("2026-08-20T12:13:00Z"));
        JdbcPaymentReadModelAdapter adapter = new JdbcPaymentReadModelAdapter(jdbc);

        var pageZero = adapter.findByLoanAndStatus(new LoanId(fixture.loanId()),
                ReportedPaymentStatus.PENDING_REVIEW, new PageRequest(0, 2));
        var pageOne = adapter.findByLoanAndStatus(new LoanId(fixture.loanId()),
                ReportedPaymentStatus.PENDING_REVIEW, new PageRequest(1, 2));

        assertEquals(3L, pageZero.totalElements());
        assertEquals(2, pageZero.totalPages());
        assertTrue(pageZero.hasNext());
        assertEquals(List.of(third, second), pageZero.content().stream()
                .map(summary -> summary.reportedPaymentId().value()).toList());
        assertEquals(new BigDecimal("95.0000"), pageZero.content().getFirst().validatedAmount().amount());
        assertEquals(List.of(first), pageOne.content().stream()
                .map(summary -> summary.reportedPaymentId().value()).toList());
        assertFalse(pageOne.hasNext());
    }

    private Fixture insertLoanFixture() {
        Instant createdAt = Instant.parse("2026-08-20T12:00:00Z");
        UUID lenderPersonId = UUID.randomUUID();
        UUID payerPersonId = UUID.randomUUID();
        UUID lenderAccountId = UUID.randomUUID();
        UUID payerAccountId = UUID.randomUUID();
        UUID loanId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO loans.people
                (person_id, first_name, status, created_at, updated_at, version)
                VALUES (?, 'INTEGRATION_TEST', 'ACTIVE', ?, ?, 0),
                       (?, 'INTEGRATION_TEST', 'ACTIVE', ?, ?, 0)
                """, lenderPersonId, Timestamp.from(createdAt), Timestamp.from(createdAt),
                payerPersonId, Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO loans.user_accounts
                (user_account_id, person_id, password_hash, status, password_changed_at,
                 authorization_version, created_at, updated_at, version)
                VALUES (?, ?, 'not-used-in-integration-tests', 'ACTIVE', ?, 0, ?, ?, 0),
                       (?, ?, 'not-used-in-integration-tests', 'ACTIVE', ?, 0, ?, ?, 0)
                """, lenderAccountId, lenderPersonId, Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt),
                payerAccountId, payerPersonId, Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO loans.loans
                (loan_id, lender_person_id, payer_person_id, currency_code, status,
                 created_at, activated_at, updated_at, version)
                VALUES (?, ?, ?, 'COP', 'ACTIVE', ?, ?, ?, 0)
                """, loanId, lenderPersonId, payerPersonId, Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt));
        jdbc.update("""
                INSERT INTO loans.loan_terms
                (loan_term_id, loan_id, version_number, status, original_principal,
                 interest_rate_percentage, rate_period, interest_method, day_count_basis,
                 amortization_method, capital_prepayment_policy, installment_count, first_due_date,
                 time_zone, effective_from, created_at, created_by_user_account_id)
                VALUES (?, ?, 1, 'ACCEPTED', 1000.0000, 1.00000000, 'ANNUAL_EFFECTIVE',
                        'SIMPLE', 'ACTUAL_365', 'FIXED_PAYMENT', 'NO_RECALCULATION', 1, ?,
                        'UTC', ?, ?, ?)
                """, UUID.randomUUID(), loanId, LocalDate.of(2026, 9, 20), Timestamp.from(createdAt),
                Timestamp.from(createdAt), lenderAccountId);
        return new Fixture(loanId, lenderPersonId, payerPersonId, lenderAccountId, payerAccountId, createdAt);
    }

    private UUID insertPayment(Fixture fixture, ReportedPaymentStatus status, String amount, Instant submittedAt) {
        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO loans.reported_payments
                (reported_payment_id, loan_id, payer_person_id, reported_by_user_account_id,
                 payment_type, reported_amount, reported_payment_date, external_reference,
                 idempotency_key, status, submitted_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'INSTALLMENT', ?, ?, 'integration-test', ?, ?, ?, ?, 0)
                """, paymentId, fixture.loanId(), fixture.payerPersonId(), fixture.payerAccountId(),
                new BigDecimal(amount), LocalDate.of(2026, 8, 20), UUID.randomUUID(), status.name(),
                Timestamp.from(submittedAt), Timestamp.from(submittedAt));
        return paymentId;
    }

    private static OutboundEmailProperties invitationProperties() {
        return new OutboundEmailProperties(true, "no-reply@integration.test", "Project Integration Tests",
                URI.create("http://localhost"), "/onboarding/payer", "/registro/verificacion",
                Duration.ofSeconds(1), 3, Base64.getEncoder().encodeToString(new byte[32]));
    }

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount), "COP");
    }

    private static Timestamp timestamp(String instant) {
        return Timestamp.from(Instant.parse(instant));
    }

    private record Fixture(
            UUID loanId,
            UUID lenderPersonId,
            UUID payerPersonId,
            UUID lenderAccountId,
            UUID payerAccountId,
            Instant createdAt) {
    }
}
