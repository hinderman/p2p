package com.project.backend.infrastructure.persistence.financial;

import com.project.backend.application.port.out.PaymentAllocationValidationPort;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.OutstandingInstallmentBalance;
import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.payment.PaymentAllocationType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validates allocation limits against committed financial history. Callers
 * acquire the loan row lock first, so two approvals for one loan cannot both
 * consume the same remaining installment component.
 */
@Component
public final class JdbcPaymentAllocationValidationAdapter implements PaymentAllocationValidationPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentAllocationValidationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockLoanForFinancialChange(LoanId loanId) {
        List<LoanId> locked = jdbcTemplate.query("SELECT loan_id FROM loans.loans WHERE loan_id = ? FOR UPDATE",
                (resultSet, rowNumber) -> new LoanId(resultSet.getObject("loan_id", java.util.UUID.class)), loanId.value());
        if (locked.isEmpty()) {
            throw new DomainRuleViolation("The payment loan does not exist");
        }
    }

    @Override
    public void validateApproval(Loan loan, ReportedPayment payment) {
        Money requestedPrincipal = Money.zero(payment.reportedAmount().currency());
        for (PaymentAllocation allocation : payment.allocations()) {
            if (allocation.type() == PaymentAllocationType.DIRECT_PRINCIPAL) {
                requestedPrincipal = requestedPrincipal.add(allocation.amount());
            } else {
                validateInstallmentAllocation(loan.id(), allocation);
                if (allocation.type() == PaymentAllocationType.INSTALLMENT_PRINCIPAL) {
                    requestedPrincipal = requestedPrincipal.add(allocation.amount());
                }
            }
        }
        if (requestedPrincipal.isPositive()) {
            validatePrincipalDoesNotExceedLoan(loan, requestedPrincipal);
        }
    }

    @Override
    public List<OutstandingInstallmentBalance> outstandingCurrentInstallments(Loan loan) {
        String currency = loan.terms().stream()
                .filter(term -> term.status() == com.project.backend.domain.loan.LoanTermStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("An active loan requires accepted terms"))
                .originalPrincipal().currency();
        return jdbcTemplate.query("""
                SELECT installment.installment_id,
                       installment.installment_number,
                       installment.due_date,
                       installment.agreed_principal,
                       installment.agreed_interest,
                       installment.agreed_fee,
                       GREATEST(installment.agreed_principal - COALESCE(SUM(CASE
                           WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'INSTALLMENT_PRINCIPAL'
                               THEN allocation.allocated_amount ELSE 0 END), 0), 0) AS outstanding_principal,
                       GREATEST(installment.agreed_interest - COALESCE(SUM(CASE
                           WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'INTEREST'
                               THEN allocation.allocated_amount ELSE 0 END), 0), 0) AS outstanding_interest,
                       GREATEST(installment.agreed_fee - COALESCE(SUM(CASE
                           WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'FEE'
                               THEN allocation.allocated_amount ELSE 0 END), 0), 0) AS outstanding_fee
                FROM loans.installments installment
                JOIN loans.payment_plans plan ON plan.payment_plan_id = installment.payment_plan_id
                JOIN loans.loan_terms term ON term.loan_term_id = plan.loan_term_id
                LEFT JOIN loans.payment_allocations allocation ON allocation.installment_id = installment.installment_id
                LEFT JOIN loans.reported_payments payment ON payment.reported_payment_id = allocation.reported_payment_id
                WHERE term.loan_id = ?
                  AND plan.status = 'CURRENT'
                GROUP BY installment.installment_id, installment.installment_number, installment.due_date,
                         installment.agreed_principal, installment.agreed_interest, installment.agreed_fee
                HAVING installment.agreed_principal > COALESCE(SUM(CASE
                           WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'INSTALLMENT_PRINCIPAL'
                               THEN allocation.allocated_amount ELSE 0 END), 0)
                    OR installment.agreed_interest > COALESCE(SUM(CASE
                           WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'INTEREST'
                               THEN allocation.allocated_amount ELSE 0 END), 0)
                    OR installment.agreed_fee > COALESCE(SUM(CASE
                           WHEN payment.status = 'APPROVED' AND allocation.allocation_type = 'FEE'
                               THEN allocation.allocated_amount ELSE 0 END), 0)
                ORDER BY installment.due_date, installment.installment_number
                """, (resultSet, rowNumber) -> new OutstandingInstallmentBalance(
                new com.project.backend.domain.valueobject.InstallmentId(
                        resultSet.getObject("installment_id", java.util.UUID.class)),
                resultSet.getInt("installment_number"), resultSet.getObject("due_date", java.time.LocalDate.class),
                money(resultSet.getBigDecimal("agreed_principal"), currency),
                money(resultSet.getBigDecimal("agreed_interest"), currency),
                money(resultSet.getBigDecimal("agreed_fee"), currency),
                money(resultSet.getBigDecimal("outstanding_principal"), currency),
                money(resultSet.getBigDecimal("outstanding_interest"), currency),
                money(resultSet.getBigDecimal("outstanding_fee"), currency)), loan.id().value());
    }

    private void validateInstallmentAllocation(LoanId loanId, PaymentAllocation allocation) {
        BigDecimal agreedAmount = jdbcTemplate.query("""
                SELECT CASE ?
                    WHEN 'INSTALLMENT_PRINCIPAL' THEN installment.agreed_principal
                    WHEN 'INTEREST' THEN installment.agreed_interest
                    WHEN 'FEE' THEN installment.agreed_fee
                END AS agreed_amount
                FROM loans.installments installment
                JOIN loans.payment_plans plan ON plan.payment_plan_id = installment.payment_plan_id
                JOIN loans.loan_terms term ON term.loan_term_id = plan.loan_term_id
                WHERE installment.installment_id = ?
                  AND term.loan_id = ?
                  AND plan.status = 'CURRENT'
                """, resultSet -> resultSet.next() ? resultSet.getBigDecimal("agreed_amount") : null,
                allocation.type().name(), allocation.installmentId().value(), loanId.value());
        if (agreedAmount == null) {
            throw new DomainRuleViolation("An allocation must target an installment in the loan's current payment plan");
        }
        BigDecimal alreadyAllocated = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocation.allocated_amount), 0)
                FROM loans.payment_allocations allocation
                JOIN loans.reported_payments payment ON payment.reported_payment_id = allocation.reported_payment_id
                WHERE allocation.installment_id = ?
                  AND allocation.allocation_type = ?
                  AND payment.status = 'APPROVED'
                """, BigDecimal.class, allocation.installmentId().value(), allocation.type().name());
        Money agreed = new Money(agreedAmount, allocation.amount().currency());
        Money applied = new Money(alreadyAllocated, allocation.amount().currency());
        if (applied.add(allocation.amount()).compareTo(agreed) > 0) {
            throw new DomainRuleViolation("The allocation exceeds the unpaid installment component");
        }
    }

    private void validatePrincipalDoesNotExceedLoan(Loan loan, Money requestedPrincipal) {
        Money originalPrincipal = loan.terms().stream()
                .filter(term -> term.status() == com.project.backend.domain.loan.LoanTermStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("An active loan requires accepted terms"))
                .originalPrincipal();
        BigDecimal alreadyAllocated = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(allocation.allocated_amount), 0)
                FROM loans.payment_allocations allocation
                JOIN loans.reported_payments payment ON payment.reported_payment_id = allocation.reported_payment_id
                WHERE payment.loan_id = ?
                  AND payment.status = 'APPROVED'
                  AND allocation.allocation_type IN ('INSTALLMENT_PRINCIPAL', 'DIRECT_PRINCIPAL')
                """, BigDecimal.class, loan.id().value());
        Money paidPrincipal = new Money(alreadyAllocated, originalPrincipal.currency());
        if (paidPrincipal.add(requestedPrincipal).compareTo(originalPrincipal) > 0) {
            throw new DomainRuleViolation("The payment would overpay the loan principal");
        }
    }

    private static Money money(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
