package com.project.backend.infrastructure.persistence.query;

import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PageRequest;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.port.out.PaymentReadModelPort;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public final class JdbcPaymentReadModelAdapter implements PaymentReadModelPort {
    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM loans.reported_payments
            WHERE loan_id = ? AND status = ?
            """;

    /**
     * Columns are listed explicitly rather than with {@code payment.*}: the page
     * only needs the projection, and a future column must not silently widen it.
     *
     * <p>The identifier breaks ties in the ordering. Without a unique tiebreaker,
     * two payments submitted in the same instant may swap places between two
     * requests, which under LIMIT/OFFSET makes a row repeat on one page and
     * disappear from another. The existing index
     * {@code ix_reported_payments_loan_status (loan_id, status, submitted_at DESC)}
     * already covers the filter and the ordering.
     */
    private static final String PAGE_SQL = """
            SELECT payment.reported_payment_id, payment.loan_id, payment.status,
                   payment.reported_amount, payment.reported_payment_date,
                   loan.currency_code, review.validated_amount
            FROM loans.reported_payments payment
            JOIN loans.loans loan ON loan.loan_id = payment.loan_id
            LEFT JOIN LATERAL (
                SELECT validated_amount FROM loans.payment_reviews
                WHERE reported_payment_id = payment.reported_payment_id
                ORDER BY review_number DESC LIMIT 1
            ) review ON true
            WHERE payment.loan_id = ? AND payment.status = ?
            ORDER BY payment.submitted_at DESC, payment.reported_payment_id DESC
            LIMIT ? OFFSET ?
            """;

    private static final RowMapper<PaymentSummary> PAYMENT_SUMMARY = (resultSet, rowNumber) -> {
        String currency = resultSet.getString("currency_code");
        return new PaymentSummary(new ReportedPaymentId(resultSet.getObject("reported_payment_id", UUID.class)),
                new LoanId(resultSet.getObject("loan_id", UUID.class)),
                ReportedPaymentStatus.valueOf(resultSet.getString("status")),
                new Money(resultSet.getBigDecimal("reported_amount"), currency),
                resultSet.getBigDecimal("validated_amount") == null ? null
                        : new Money(resultSet.getBigDecimal("validated_amount"), currency),
                resultSet.getObject("reported_payment_date", LocalDate.class));
    };

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentReadModelAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Offset pagination is used because the lender navigates this list by page
     * number and needs a total. Keyset pagination would scale better for deep
     * pages, but it cannot answer "page 4 of 9"; the pending queue of a single
     * loan never reaches the depth where that trade-off would pay off.
     */
    @Override
    public Page<PaymentSummary> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status, PageRequest pageRequest) {
        Long counted = jdbcTemplate.queryForObject(COUNT_SQL, Long.class, loanId.value(), status.name());
        long totalElements = counted == null ? 0L : counted;
        if (pageRequest.offset() >= totalElements) {
            return Page.empty(pageRequest, totalElements);
        }
        List<PaymentSummary> content = jdbcTemplate.query(PAGE_SQL, PAYMENT_SUMMARY,
                loanId.value(), status.name(), pageRequest.size(), pageRequest.offset());
        return Page.of(content, pageRequest, totalElements);
    }
}
