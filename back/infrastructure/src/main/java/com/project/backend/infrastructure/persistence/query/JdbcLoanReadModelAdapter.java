package com.project.backend.infrastructure.persistence.query;

import com.project.backend.application.dto.InstallmentDetail;
import com.project.backend.application.dto.LoanDetail;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.LoanTermsDetail;
import com.project.backend.application.dto.PaymentPlanDetail;
import com.project.backend.application.port.out.LoanReadModelPort;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.loan.PaymentPlanReason;
import com.project.backend.domain.loan.PaymentPlanStatus;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PaymentPlanId;
import com.project.backend.domain.valueobject.PersonId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    @Override
    public Optional<LoanDetail> findDetail(LoanId loanId, PersonId participantId) {
        List<DetailHeader> headers = jdbcTemplate.query("""
                SELECT loan.loan_id,
                       CASE WHEN loan.lender_person_id = ? THEN loan.payer_person_id ELSE loan.lender_person_id END AS counterparty_person_id,
                       loan.status, loan.currency_code, loan.created_at,
                       term.original_principal, term.version_number AS term_version,
                       term.interest_rate_percentage, term.rate_period, term.interest_method,
                       term.day_count_basis, term.amortization_method, term.capital_prepayment_policy,
                       term.installment_count, term.first_due_date, term.time_zone,
                       plan.payment_plan_id, plan.version_number AS plan_version, plan.reason AS plan_reason,
                       plan.status AS plan_status,
                       COALESCE(principal.principal_paid, 0) AS principal_paid
                FROM loans.loans loan
                JOIN LATERAL (
                    SELECT candidate.* FROM loans.loan_terms candidate
                    WHERE candidate.loan_id = loan.loan_id
                    ORDER BY CASE candidate.status WHEN 'ACCEPTED' THEN 0 WHEN 'PROPOSED' THEN 1 ELSE 2 END,
                             candidate.version_number DESC
                    LIMIT 1
                ) term ON TRUE
                LEFT JOIN loans.payment_plans plan ON plan.loan_term_id = term.loan_term_id AND plan.status = 'CURRENT'
                LEFT JOIN (
                    SELECT payment.loan_id, SUM(allocation.allocated_amount) AS principal_paid
                    FROM loans.reported_payments payment
                    JOIN loans.payment_allocations allocation ON allocation.reported_payment_id = payment.reported_payment_id
                    WHERE payment.status = 'APPROVED'
                      AND allocation.allocation_type IN ('INSTALLMENT_PRINCIPAL', 'DIRECT_PRINCIPAL')
                    GROUP BY payment.loan_id
                ) principal ON principal.loan_id = loan.loan_id
                WHERE loan.loan_id = ? AND ? IN (loan.lender_person_id, loan.payer_person_id)
                """, (resultSet, rowNumber) -> {
            String currency = resultSet.getString("currency_code");
            Money originalPrincipal = money(resultSet.getBigDecimal("original_principal"), currency);
            Money paidPrincipal = money(resultSet.getBigDecimal("principal_paid"), currency);
            UUID planId = resultSet.getObject("payment_plan_id", UUID.class);
            return new DetailHeader(
                    new LoanId(resultSet.getObject("loan_id", UUID.class)),
                    new PersonId(resultSet.getObject("counterparty_person_id", UUID.class)),
                    LoanStatus.valueOf(resultSet.getString("status")), originalPrincipal,
                    originalPrincipal.subtract(paidPrincipal), instant(resultSet, "created_at"),
                    new LoanTermsDetail(resultSet.getInt("term_version"), resultSet.getBigDecimal("interest_rate_percentage"),
                            RatePeriod.valueOf(resultSet.getString("rate_period")),
                            InterestCalculationMethod.valueOf(resultSet.getString("interest_method")),
                            DayCountBasis.valueOf(resultSet.getString("day_count_basis")),
                            AmortizationMethod.valueOf(resultSet.getString("amortization_method")),
                            CapitalPrepaymentPolicy.valueOf(resultSet.getString("capital_prepayment_policy")),
                            resultSet.getInt("installment_count"), resultSet.getObject("first_due_date", java.time.LocalDate.class),
                            resultSet.getString("time_zone")),
                    planId == null ? null : new PlanHeader(new PaymentPlanId(planId), resultSet.getInt("plan_version"),
                            PaymentPlanReason.valueOf(resultSet.getString("plan_reason")),
                            PaymentPlanStatus.valueOf(resultSet.getString("plan_status"))), currency);
        }, participantId.value(), loanId.value(), participantId.value());
        if (headers.isEmpty()) return Optional.empty();
        DetailHeader header = headers.getFirst();
        PaymentPlanDetail paymentPlan = null;
        if (header.plan() != null) {
            List<InstallmentDetail> installments = findInstallments(header.plan().id(), header.currency());
            paymentPlan = new PaymentPlanDetail(header.plan().id(), header.plan().version(), header.plan().reason(),
                    header.plan().status(), installments);
        }
        return Optional.of(new LoanDetail(header.loanId(), header.counterpartyPersonId(), header.status(),
                header.originalPrincipal(), header.outstandingBalance(), header.createdAt(), header.terms(), paymentPlan));
    }

    private List<InstallmentDetail> findInstallments(PaymentPlanId planId, String currency) {
        return jdbcTemplate.query("""
                SELECT installment.installment_id, installment.installment_number, installment.due_date,
                       installment.agreed_principal, installment.agreed_interest, installment.agreed_fee,
                       COALESCE(SUM(CASE WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'INSTALLMENT_PRINCIPAL' THEN allocation.allocated_amount ELSE 0 END), 0) AS paid_principal,
                       COALESCE(SUM(CASE WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'INTEREST' THEN allocation.allocated_amount ELSE 0 END), 0) AS paid_interest,
                       COALESCE(SUM(CASE WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'FEE' THEN allocation.allocated_amount ELSE 0 END), 0) AS paid_fee
                FROM loans.installments installment
                LEFT JOIN loans.payment_allocations allocation ON allocation.installment_id = installment.installment_id
                LEFT JOIN loans.reported_payments payment ON payment.reported_payment_id = allocation.reported_payment_id
                WHERE installment.payment_plan_id = ?
                GROUP BY installment.installment_id, installment.installment_number, installment.due_date,
                         installment.agreed_principal, installment.agreed_interest, installment.agreed_fee
                ORDER BY installment.installment_number
                """, (resultSet, rowNumber) -> {
            Money agreedPrincipal = money(resultSet.getBigDecimal("agreed_principal"), currency);
            Money agreedInterest = money(resultSet.getBigDecimal("agreed_interest"), currency);
            Money agreedFee = money(resultSet.getBigDecimal("agreed_fee"), currency);
            Money paidPrincipal = money(resultSet.getBigDecimal("paid_principal"), currency);
            Money paidInterest = money(resultSet.getBigDecimal("paid_interest"), currency);
            Money paidFee = money(resultSet.getBigDecimal("paid_fee"), currency);
            return new InstallmentDetail(new InstallmentId(resultSet.getObject("installment_id", UUID.class)),
                    resultSet.getInt("installment_number"), resultSet.getObject("due_date", java.time.LocalDate.class),
                    agreedPrincipal, agreedInterest, agreedFee, paidPrincipal, paidInterest, paidFee,
                    agreedPrincipal.subtract(paidPrincipal), agreedInterest.subtract(paidInterest), agreedFee.subtract(paidFee));
        }, planId.value());
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

    private static Money money(java.math.BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    private record PlanHeader(PaymentPlanId id, int version, PaymentPlanReason reason, PaymentPlanStatus status) { }
    private record DetailHeader(LoanId loanId, PersonId counterpartyPersonId, LoanStatus status,
                                Money originalPrincipal, Money outstandingBalance, Instant createdAt,
                                LoanTermsDetail terms, PlanHeader plan, String currency) { }
}
