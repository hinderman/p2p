package com.project.backend.infrastructure.persistence.query;

import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.port.out.LoanReadModelPort;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.instant;

@Component
public final class JdbcLoanReadModelAdapter implements LoanReadModelPort {
    private static final String LENDER_QUERY = """
            SELECT loan.loan_id, loan.payer_person_id AS counterparty_person_id, loan.status, loan.currency_code,
                   loan.created_at, term.original_principal,
                   COALESCE(allocations.principal_paid, 0) AS principal_paid
            FROM loans.loans loan
            JOIN loans.loan_terms term ON term.loan_id = loan.loan_id
                AND term.version_number = (SELECT MIN(version_number) FROM loans.loan_terms WHERE loan_id = loan.loan_id)
            LEFT JOIN (
                SELECT payment.loan_id, SUM(allocation.allocated_amount) AS principal_paid
                FROM loans.reported_payments payment
                JOIN loans.payment_allocations allocation ON allocation.reported_payment_id = payment.reported_payment_id
                WHERE payment.status = 'APPROVED'
                  AND allocation.allocation_type IN ('INSTALLMENT_PRINCIPAL', 'DIRECT_PRINCIPAL')
                GROUP BY payment.loan_id
            ) allocations ON allocations.loan_id = loan.loan_id
            WHERE loan.lender_person_id = ? ORDER BY loan.created_at DESC
            """;
    private static final String PAYER_QUERY = LENDER_QUERY.replace("loan.payer_person_id AS counterparty_person_id", "loan.lender_person_id AS counterparty_person_id")
            .replace("WHERE loan.lender_person_id = ?", "WHERE loan.payer_person_id = ?");

    private final JdbcTemplate jdbcTemplate;

    public JdbcLoanReadModelAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<LoanSummary> findByLender(PersonId lenderId) {
        return query(LENDER_QUERY, lenderId.value());
    }

    @Override
    public List<LoanSummary> findByPayer(PersonId payerId) {
        return query(PAYER_QUERY, payerId.value());
    }

    private List<LoanSummary> query(String sql, UUID personId) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            Money principal = new Money(resultSet.getBigDecimal("original_principal"), resultSet.getString("currency_code"));
            Money principalPaid = new Money(resultSet.getBigDecimal("principal_paid"), resultSet.getString("currency_code"));
            return new LoanSummary(new LoanId(resultSet.getObject("loan_id", UUID.class)),
                    new PersonId(resultSet.getObject("counterparty_person_id", UUID.class)),
                    LoanStatus.valueOf(resultSet.getString("status")), principal, principal.subtract(principalPaid),
                    instant(resultSet, "created_at"));
        }, personId);
    }
}
