package com.project.backend.infrastructure.persistence.repository;

import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.payment.PaymentAllocationType;
import com.project.backend.domain.payment.PaymentProof;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public final class JdbcReportedPaymentRepository implements ReportedPaymentRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcReportedPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ReportedPayment> findById(ReportedPaymentId id) {
        return findPayments("SELECT payment.*, loan.currency_code FROM loans.reported_payments payment JOIN loans.loans loan ON loan.loan_id = payment.loan_id WHERE payment.reported_payment_id = ?", id.value())
                .stream().findFirst();
    }

    @Override
    public List<ReportedPayment> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status) {
        return findPayments("""
                SELECT payment.*, loan.currency_code FROM loans.reported_payments payment
                JOIN loans.loans loan ON loan.loan_id = payment.loan_id
                WHERE payment.loan_id = ? AND payment.status = ?
                """, loanId.value(), status.name());
    }

    @Override
    public ReportedPayment save(ReportedPayment payment) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE loans.reported_payments
                SET status = ?, updated_at = ?, version = version + 1
                WHERE reported_payment_id = ?
                """, payment.status().name(), now, payment.id().value());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO loans.reported_payments
                    (reported_payment_id, loan_id, payer_person_id, reported_by_user_account_id, payment_type,
                     reported_amount, reported_payment_date, external_reference, idempotency_key, status,
                     submitted_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, payment.id().value(), payment.loanId().value(), payment.payerPersonId().value(),
                    payment.reportingAccountId().value(), payment.type().name(), payment.reportedAmount().amount(),
                    payment.reportedPaymentDate(), payment.externalReference(), UUID.randomUUID(), payment.status().name(),
                    payment.createdAt(), now);
        }
        persistEvidence(payment);
        persistAllocations(payment, now);
        persistReviewOrReversal(payment, now);
        return payment;
    }

    @Override
    public void delete(ReportedPaymentId id) {
        jdbcTemplate.update("DELETE FROM loans.payment_allocations WHERE reported_payment_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.payment_evidence WHERE reported_payment_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.payment_reviews WHERE reported_payment_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.payment_reversals WHERE reported_payment_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.reported_payments WHERE reported_payment_id = ?", id.value());
    }

    @Override
    public boolean exists(ReportedPaymentId id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM loans.reported_payments WHERE reported_payment_id = ?)", Boolean.class, id.value()));
    }

    private List<ReportedPayment> findPayments(String sql, Object... arguments) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            UUID paymentId = resultSet.getObject("reported_payment_id", UUID.class);
            String currency = resultSet.getString("currency_code");
            ReportedPaymentStatus status = ReportedPaymentStatus.valueOf(resultSet.getString("status"));
            ReviewData review = findReview(paymentId, status, currency);
            return ReportedPayment.rehydrate(
                    new ReportedPaymentId(paymentId), new LoanId(resultSet.getObject("loan_id", UUID.class)),
                    new PersonId(resultSet.getObject("payer_person_id", UUID.class)),
                    new UserAccountId(resultSet.getObject("reported_by_user_account_id", UUID.class)),
                    PaymentType.valueOf(resultSet.getString("payment_type")),
                    money(resultSet.getBigDecimal("reported_amount"), currency),
                    resultSet.getObject("reported_payment_date", LocalDate.class), resultSet.getString("external_reference"),
                    status, resultSet.getObject("submitted_at", Instant.class), findProofs(paymentId),
                    findAllocations(paymentId, currency), review.validatedAmount(), review.rejectionReason(),
                    findReversalReason(paymentId));
        }, arguments);
    }

    private List<PaymentProof> findProofs(UUID paymentId) {
        return jdbcTemplate.query("""
                SELECT object.sha256, evidence.stored_object_id FROM loans.payment_evidence evidence
                JOIN loans.stored_objects object ON object.stored_object_id = evidence.stored_object_id
                WHERE evidence.reported_payment_id = ?
                """, (resultSet, rowNumber) -> new PaymentProof(
                resultSet.getObject("stored_object_id", UUID.class), resultSet.getString("sha256")), paymentId);
    }

    private List<PaymentAllocation> findAllocations(UUID paymentId, String currency) {
        return jdbcTemplate.query("SELECT * FROM loans.payment_allocations WHERE reported_payment_id = ?", (resultSet, rowNumber) -> {
            UUID installmentId = resultSet.getObject("installment_id", UUID.class);
            return new PaymentAllocation(installmentId == null ? null : new InstallmentId(installmentId),
                    PaymentAllocationType.valueOf(resultSet.getString("allocation_type")),
                    money(resultSet.getBigDecimal("allocated_amount"), currency));
        }, paymentId);
    }

    private ReviewData findReview(UUID paymentId, ReportedPaymentStatus status, String currency) {
        if (status != ReportedPaymentStatus.APPROVED && status != ReportedPaymentStatus.REJECTED) {
            return new ReviewData(null, null);
        }
        List<ReviewData> reviews = jdbcTemplate.query("""
                SELECT validated_amount, reason FROM loans.payment_reviews
                WHERE reported_payment_id = ? ORDER BY review_number DESC LIMIT 1
                """, (resultSet, rowNumber) -> new ReviewData(
                resultSet.getBigDecimal("validated_amount") == null ? null : money(resultSet.getBigDecimal("validated_amount"), currency),
                resultSet.getString("reason")), paymentId);
        return reviews.isEmpty() ? new ReviewData(null, null) : reviews.getFirst();
    }

    private String findReversalReason(UUID paymentId) {
        List<String> reasons = jdbcTemplate.queryForList(
                "SELECT reason FROM loans.payment_reversals WHERE reported_payment_id = ?", String.class, paymentId);
        return reasons.isEmpty() ? null : reasons.getFirst();
    }

    private void persistEvidence(ReportedPayment payment) {
        for (PaymentProof proof : payment.proofs()) {
            jdbcTemplate.update("""
                    INSERT INTO loans.payment_evidence (payment_evidence_id, reported_payment_id, stored_object_id, created_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (stored_object_id) DO NOTHING
                    """, UUID.randomUUID(), payment.id().value(), proof.storedObjectId(), payment.createdAt());
        }
    }

    private void persistAllocations(ReportedPayment payment, Instant now) {
        if (payment.allocations().isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM loans.payment_allocations WHERE reported_payment_id = ?", payment.id().value());
        for (PaymentAllocation allocation : payment.allocations()) {
            jdbcTemplate.update("""
                    INSERT INTO loans.payment_allocations
                    (payment_allocation_id, reported_payment_id, installment_id, allocation_type, allocated_amount, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), payment.id().value(),
                    allocation.installmentId() == null ? null : allocation.installmentId().value(),
                    allocation.type().name(), allocation.amount().amount(), now);
        }
    }

    private void persistReviewOrReversal(ReportedPayment payment, Instant now) {
        if (payment.status() == ReportedPaymentStatus.APPROVED && payment.validatedAmount() != null) {
            jdbcTemplate.update("""
                    INSERT INTO loans.payment_reviews
                    (payment_review_id, reported_payment_id, review_number, reviewer_user_account_id, decision,
                     validated_amount, reviewed_at)
                    SELECT ?, ?, 1, account.user_account_id, 'APPROVED', ?, ?
                    FROM loans.loans loan JOIN loans.user_accounts account ON account.person_id = loan.lender_person_id
                    WHERE loan.loan_id = ?
                    ON CONFLICT (reported_payment_id, review_number) DO NOTHING
                    """, UUID.randomUUID(), payment.id().value(), payment.validatedAmount().amount(), now, payment.loanId().value());
        } else if (payment.status() == ReportedPaymentStatus.REJECTED && payment.rejectionReason() != null) {
            jdbcTemplate.update("""
                    INSERT INTO loans.payment_reviews
                    (payment_review_id, reported_payment_id, review_number, reviewer_user_account_id, decision, reason, reviewed_at)
                    SELECT ?, ?, 1, account.user_account_id, 'REJECTED', ?, ?
                    FROM loans.loans loan JOIN loans.user_accounts account ON account.person_id = loan.lender_person_id
                    WHERE loan.loan_id = ?
                    ON CONFLICT (reported_payment_id, review_number) DO NOTHING
                    """, UUID.randomUUID(), payment.id().value(), payment.rejectionReason(), now, payment.loanId().value());
        } else if (payment.status() == ReportedPaymentStatus.REVERSED && payment.reversalReason() != null) {
            jdbcTemplate.update("""
                    INSERT INTO loans.payment_reversals
                    (payment_reversal_id, reported_payment_id, reviewer_user_account_id, reason, reversed_at)
                    SELECT ?, ?, account.user_account_id, ?, ?
                    FROM loans.loans loan JOIN loans.user_accounts account ON account.person_id = loan.lender_person_id
                    WHERE loan.loan_id = ?
                    ON CONFLICT (reported_payment_id) DO NOTHING
                    """, UUID.randomUUID(), payment.id().value(), payment.reversalReason(), now, payment.loanId().value());
        }
    }

    private static Money money(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    private record ReviewData(Money validatedAmount, String rejectionReason) {
    }
}
