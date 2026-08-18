package com.project.backend.infrastructure.persistence.query;

import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.port.out.PaymentReadModelPort;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public final class JdbcPaymentReadModelAdapter implements PaymentReadModelPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentReadModelAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PaymentSummary> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status) {
        return jdbcTemplate.query("""
                SELECT payment.*, loan.currency_code,
                       review.validated_amount
                FROM loans.reported_payments payment
                JOIN loans.loans loan ON loan.loan_id = payment.loan_id
                LEFT JOIN LATERAL (
                    SELECT validated_amount FROM loans.payment_reviews
                    WHERE reported_payment_id = payment.reported_payment_id
                    ORDER BY review_number DESC LIMIT 1
                ) review ON true
                WHERE payment.loan_id = ? AND payment.status = ?
                ORDER BY payment.submitted_at DESC
                """, (resultSet, rowNumber) -> {
            String currency = resultSet.getString("currency_code");
            return new PaymentSummary(new ReportedPaymentId(resultSet.getObject("reported_payment_id", UUID.class)),
                    new LoanId(resultSet.getObject("loan_id", UUID.class)),
                    ReportedPaymentStatus.valueOf(resultSet.getString("status")),
                    new Money(resultSet.getBigDecimal("reported_amount"), currency),
                    resultSet.getBigDecimal("validated_amount") == null ? null
                            : new Money(resultSet.getBigDecimal("validated_amount"), currency),
                    resultSet.getObject("reported_payment_date", java.time.LocalDate.class));
        }, loanId.value(), status.name());
    }
}
