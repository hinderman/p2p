package com.project.backend.infrastructure.persistence.repository;

import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.Installment;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.NonBusinessDayAdjustment;
import com.project.backend.domain.loan.PaymentFrequency;
import com.project.backend.domain.loan.PaymentPlan;
import com.project.backend.domain.loan.PaymentPlanReason;
import com.project.backend.domain.loan.PaymentPlanStatus;
import com.project.backend.domain.loan.PaymentScheduleRule;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.InterestRate;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PaymentPlanId;
import com.project.backend.domain.valueobject.PersonId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public final class JdbcLoanRepository implements LoanRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLoanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Loan> findById(LoanId id) {
        return findLoan("SELECT * FROM loans.loans WHERE loan_id = ?", id.value());
    }

    @Override
    public List<Loan> findActiveByLender(PersonId lenderId) {
        return findLoans("SELECT * FROM loans.loans WHERE lender_person_id = ? AND status = 'ACTIVE'", lenderId.value());
    }

    @Override
    public List<Loan> findActiveByPayer(PersonId payerId) {
        return findLoans("SELECT * FROM loans.loans WHERE payer_person_id = ? AND status = 'ACTIVE'", payerId.value());
    }

    @Override
    public Loan save(Loan loan) {
        Instant now = Instant.now();
        String currency = loan.terms().getFirst().originalPrincipal().currency();
        int updated = jdbcTemplate.update("""
                UPDATE loans.loans SET status = ?, currency_code = ?, updated_at = ?, version = version + 1,
                    activated_at = CASE WHEN ? = 'ACTIVE' THEN COALESCE(activated_at, ?) ELSE activated_at END,
                    cancelled_at = CASE WHEN ? = 'CANCELLED' THEN COALESCE(cancelled_at, ?) ELSE cancelled_at END
                WHERE loan_id = ?
                """, loan.status().name(), currency, now, loan.status().name(), now,
                loan.status().name(), now, loan.id().value());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO loans.loans
                    (loan_id, lender_person_id, payer_person_id, currency_code, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                    """, loan.id().value(), loan.lenderPersonId().value(), loan.payerPersonId().value(),
                    currency, loan.status().name(), loan.createdAt(), now);
        }
        UUID lenderAccountId = lenderAccountId(loan.lenderPersonId());
        for (LoanTerms terms : loan.terms()) {
            persistTerms(loan, terms, lenderAccountId, now);
        }
        if (loan.currentPaymentPlan() != null) {
            persistCurrentPlan(loan.currentPaymentPlan(), lenderAccountId, now);
        }
        return loan;
    }

    @Override
    public void delete(LoanId id) {
        jdbcTemplate.update("DELETE FROM loans.payment_allocations WHERE reported_payment_id IN (SELECT reported_payment_id FROM loans.reported_payments WHERE loan_id = ?)", id.value());
        jdbcTemplate.update("DELETE FROM loans.reported_payments WHERE loan_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.installments WHERE payment_plan_id IN (SELECT payment_plan_id FROM loans.payment_plans WHERE loan_term_id IN (SELECT loan_term_id FROM loans.loan_terms WHERE loan_id = ?))", id.value());
        jdbcTemplate.update("DELETE FROM loans.payment_plans WHERE loan_term_id IN (SELECT loan_term_id FROM loans.loan_terms WHERE loan_id = ?)", id.value());
        jdbcTemplate.update("DELETE FROM loans.payment_schedule_rule_days WHERE payment_schedule_rule_id IN (SELECT payment_schedule_rule_id FROM loans.payment_schedule_rules WHERE loan_term_id IN (SELECT loan_term_id FROM loans.loan_terms WHERE loan_id = ?))", id.value());
        jdbcTemplate.update("DELETE FROM loans.payment_schedule_rules WHERE loan_term_id IN (SELECT loan_term_id FROM loans.loan_terms WHERE loan_id = ?)", id.value());
        jdbcTemplate.update("DELETE FROM loans.loan_terms WHERE loan_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.loans WHERE loan_id = ?", id.value());
    }

    @Override
    public boolean exists(LoanId id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM loans.loans WHERE loan_id = ?)", Boolean.class, id.value()));
    }

    private Optional<Loan> findLoan(String sql, Object argument) {
        return findLoans(sql, argument).stream().findFirst();
    }

    private List<Loan> findLoans(String sql, Object argument) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            UUID loanId = resultSet.getObject("loan_id", UUID.class);
            List<LoanTerms> terms = findTerms(loanId, resultSet.getString("currency_code"));
            return Loan.rehydrate(new LoanId(loanId),
                    new PersonId(resultSet.getObject("lender_person_id", UUID.class)),
                    new PersonId(resultSet.getObject("payer_person_id", UUID.class)),
                    LoanStatus.valueOf(resultSet.getString("status")), terms,
                    findCurrentPaymentPlan(loanId, resultSet.getString("currency_code")),
                    resultSet.getObject("created_at", Instant.class));
        }, argument);
    }

    private List<LoanTerms> findTerms(UUID loanId, String currency) {
        return jdbcTemplate.query("SELECT * FROM loans.loan_terms WHERE loan_id = ? ORDER BY version_number", (resultSet, rowNumber) -> {
            UUID termId = resultSet.getObject("loan_term_id", UUID.class);
            return new LoanTerms(new LoanTermId(termId), resultSet.getInt("version_number"),
                    money(resultSet.getBigDecimal("original_principal"), currency),
                    new InterestRate(resultSet.getBigDecimal("interest_rate_percentage")),
                    RatePeriod.valueOf(resultSet.getString("rate_period")),
                    InterestCalculationMethod.valueOf(resultSet.getString("interest_method")),
                    DayCountBasis.valueOf(resultSet.getString("day_count_basis")),
                    AmortizationMethod.valueOf(resultSet.getString("amortization_method")),
                    CapitalPrepaymentPolicy.valueOf(resultSet.getString("capital_prepayment_policy")),
                    resultSet.getInt("installment_count"), resultSet.getObject("first_due_date", LocalDate.class),
                    ZoneId.of(resultSet.getString("time_zone")), findScheduleRule(termId),
                    LoanTermStatus.valueOf(resultSet.getString("status")));
        }, loanId);
    }

    private PaymentScheduleRule findScheduleRule(UUID termId) {
        return jdbcTemplate.query("SELECT * FROM loans.payment_schedule_rules WHERE loan_term_id = ?", resultSet -> {
            if (!resultSet.next()) {
                throw new IllegalStateException("Loan terms are missing their payment schedule rule");
            }
            UUID ruleId = resultSet.getObject("payment_schedule_rule_id", UUID.class);
            Set<Integer> days = Set.copyOf(jdbcTemplate.queryForList(
                    "SELECT day_of_month FROM loans.payment_schedule_rule_days WHERE payment_schedule_rule_id = ? ORDER BY day_of_month",
                    Integer.class, ruleId));
            return new PaymentScheduleRule(PaymentFrequency.valueOf(resultSet.getString("frequency")),
                    resultSet.getObject("interval_days", Integer.class), days,
                    NonBusinessDayAdjustment.valueOf(resultSet.getString("non_business_day_adjustment")));
        }, termId);
    }

    private PaymentPlan findCurrentPaymentPlan(UUID loanId, String currency) {
        List<PaymentPlan> plans = jdbcTemplate.query("""
                SELECT plan.* FROM loans.payment_plans plan
                JOIN loans.loan_terms term ON term.loan_term_id = plan.loan_term_id
                WHERE term.loan_id = ? AND plan.status = 'CURRENT'
                """, (resultSet, rowNumber) -> {
            UUID planId = resultSet.getObject("payment_plan_id", UUID.class);
            List<Installment> installments = jdbcTemplate.query("""
                    SELECT * FROM loans.installments WHERE payment_plan_id = ? ORDER BY installment_number
                    """, (installmentResultSet, installmentRowNumber) -> new Installment(
                    new InstallmentId(installmentResultSet.getObject("installment_id", UUID.class)),
                    installmentResultSet.getInt("installment_number"),
                    installmentResultSet.getObject("due_date", LocalDate.class),
                    money(installmentResultSet.getBigDecimal("agreed_principal"), currency),
                    money(installmentResultSet.getBigDecimal("agreed_interest"), currency),
                    money(installmentResultSet.getBigDecimal("agreed_fee"), currency)), planId);
            return new PaymentPlan(new PaymentPlanId(planId),
                    new LoanTermId(resultSet.getObject("loan_term_id", UUID.class)),
                    resultSet.getInt("version_number"), PaymentPlanReason.valueOf(resultSet.getString("reason")),
                    installments, PaymentPlanStatus.valueOf(resultSet.getString("status")),
                    resultSet.getObject("created_at", Instant.class), resultSet.getObject("superseded_at", Instant.class));
        }, loanId);
        return plans.isEmpty() ? null : plans.getFirst();
    }

    private void persistTerms(Loan loan, LoanTerms terms, UUID lenderAccountId, Instant now) {
        int updated = jdbcTemplate.update("UPDATE loans.loan_terms SET status = ? WHERE loan_term_id = ?",
                terms.status().name(), terms.id().value());
        if (updated != 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO loans.loan_terms
                (loan_term_id, loan_id, version_number, status, original_principal, interest_rate_percentage,
                 rate_period, interest_method, day_count_basis, amortization_method, capital_prepayment_policy,
                 installment_count, first_due_date, time_zone, effective_from, created_at, created_by_user_account_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, terms.id().value(), loan.id().value(), terms.versionNumber(), terms.status().name(),
                terms.originalPrincipal().amount(), terms.interestRate().percentage(), terms.ratePeriod().name(),
                terms.interestCalculationMethod().name(), terms.dayCountBasis().name(), terms.amortizationMethod().name(),
                terms.capitalPrepaymentPolicy().name(), terms.installmentCount(), terms.firstDueDate(), terms.timeZone().getId(),
                now, now, lenderAccountId);
        persistScheduleRule(terms.id(), terms.paymentScheduleRule(), now);
    }

    private void persistScheduleRule(LoanTermId termId, PaymentScheduleRule rule, Instant now) {
        UUID ruleId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO loans.payment_schedule_rules
                (payment_schedule_rule_id, loan_term_id, frequency, interval_days, non_business_day_adjustment, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ruleId, termId.value(), rule.frequency().name(), rule.intervalDays(),
                rule.nonBusinessDayAdjustment().name(), now);
        for (Integer day : rule.daysOfMonth()) {
            jdbcTemplate.update("INSERT INTO loans.payment_schedule_rule_days (payment_schedule_rule_id, day_of_month) VALUES (?, ?)",
                    ruleId, day);
        }
    }

    private void persistCurrentPlan(PaymentPlan plan, UUID lenderAccountId, Instant now) {
        jdbcTemplate.update("""
                UPDATE loans.payment_plans SET status = 'SUPERSEDED', superseded_at = ?
                WHERE loan_term_id = ? AND status = 'CURRENT' AND payment_plan_id <> ?
                """, now, plan.loanTermId().value(), plan.id().value());
        int updated = jdbcTemplate.update("""
                UPDATE loans.payment_plans SET status = ?, reason = ?, superseded_at = ? WHERE payment_plan_id = ?
                """, plan.status().name(), plan.reason().name(), plan.replacedAt(), plan.id().value());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO loans.payment_plans
                    (payment_plan_id, loan_term_id, version_number, status, reason, created_at, superseded_at, created_by_user_account_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, plan.id().value(), plan.loanTermId().value(), plan.versionNumber(), plan.status().name(),
                    plan.reason().name(), plan.createdAt(), plan.replacedAt(), lenderAccountId);
            for (Installment installment : plan.installments()) {
                jdbcTemplate.update("""
                        INSERT INTO loans.installments
                        (installment_id, payment_plan_id, installment_number, due_date, agreed_principal, agreed_interest, agreed_fee, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, installment.id().value(), plan.id().value(), installment.number(), installment.dueDate(),
                        installment.agreedPrincipal().amount(), installment.agreedInterest().amount(), installment.agreedFee().amount(),
                        plan.createdAt());
            }
        }
    }

    private UUID lenderAccountId(PersonId lenderId) {
        return jdbcTemplate.queryForObject(
                "SELECT user_account_id FROM loans.user_accounts WHERE person_id = ?", UUID.class, lenderId.value());
    }

    private static Money money(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }
}
