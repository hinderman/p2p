package com.project.backend.domain.service;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.Installment;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.NonBusinessDayAdjustment;
import com.project.backend.domain.loan.OutstandingInstallmentBalance;
import com.project.backend.domain.loan.PaymentFrequency;
import com.project.backend.domain.loan.PaymentPlan;
import com.project.backend.domain.loan.PaymentPlanReason;
import com.project.backend.domain.loan.PaymentPlanStatus;
import com.project.backend.domain.loan.PaymentScheduleRule;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.payment.PaymentAllocationType;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.InstallmentId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure-Java contractual schedule generator.
 *
 * <p>Interest is accrued from the loan acceptance date to the first due date
 * and then between due dates. Amounts are rounded once per contractual
 * installment to {@link Money#SCALE} using half-even rounding; any principal
 * residual is assigned to the final installment. Fees are intentionally zero:
 * no fee policy exists in {@link LoanTerms} yet.</p>
 */
public final class StandardPaymentPlanGenerator implements PaymentPlanGenerator {
    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final BigDecimal THREE_HUNDRED_SIXTY = BigDecimal.valueOf(360);
    private static final BigDecimal THREE_HUNDRED_SIXTY_FIVE = BigDecimal.valueOf(365);

    private final BusinessDayCalendar businessDayCalendar;

    public StandardPaymentPlanGenerator() {
        this(new WeekendBusinessDayCalendar());
    }

    public StandardPaymentPlanGenerator(BusinessDayCalendar businessDayCalendar) {
        this.businessDayCalendar = Objects.requireNonNull(businessDayCalendar, "The business-day calendar is required");
    }

    @Override
    public PaymentPlan generateInitial(PaymentPlanId paymentPlanId, LoanTerms terms, java.time.Instant generatedAt) {
        Objects.requireNonNull(paymentPlanId, "The payment plan identifier is required");
        Objects.requireNonNull(terms, "The loan terms are required");
        Objects.requireNonNull(generatedAt, "The generation time is required");

        LocalDate accrualStart = toLocalDate(generatedAt, terms.timeZone());
        List<LocalDate> dueDates = generateDueDates(terms.firstDueDate(), terms.paymentScheduleRule(), terms.installmentCount());
        return buildPlan(paymentPlanId, terms, 1, PaymentPlanReason.ORIGINAL, terms.originalPrincipal(),
                accrualStart, dueDates, generatedAt, false, null, null,
                Money.zero(terms.originalPrincipal().currency()), Money.zero(terms.originalPrincipal().currency()), List.of());
    }

    @Override
    public PaymentPlan recalculateAfterCapitalPrepayment(
            PaymentPlanId paymentPlanId,
            Loan loan,
            ReportedPayment approvedPayment,
            List<OutstandingInstallmentBalance> outstandingInstallments,
            java.time.Instant generatedAt) {
        Objects.requireNonNull(paymentPlanId, "The payment plan identifier is required");
        Objects.requireNonNull(loan, "The loan is required");
        Objects.requireNonNull(approvedPayment, "The approved payment is required");
        outstandingInstallments = List.copyOf(Objects.requireNonNull(outstandingInstallments,
                "The outstanding installment balances are required"));
        Objects.requireNonNull(generatedAt, "The generation time is required");
        if (approvedPayment.status() != com.project.backend.domain.payment.ReportedPaymentStatus.APPROVED
                || approvedPayment.type() != PaymentType.CAPITAL_PREPAYMENT) {
            throw new DomainRuleViolation("Only an approved principal prepayment can recalculate a payment plan");
        }

        LoanTerms terms = loan.terms().stream()
                .filter(term -> term.status() == LoanTermStatus.ACCEPTED)
                .findFirst()
                .orElseThrow(() -> new DomainRuleViolation("An active loan requires accepted loan terms"));
        if (terms.capitalPrepaymentPolicy() == CapitalPrepaymentPolicy.NO_RECALCULATION) {
            throw new DomainRuleViolation("The loan terms do not permit recalculation after a principal prepayment");
        }
        PaymentPlan currentPlan = Objects.requireNonNull(loan.currentPaymentPlan(), "The loan requires a current payment plan");
        Money prepaymentAmount = approvedPayment.allocations().stream()
                .filter(allocation -> allocation.type() == PaymentAllocationType.DIRECT_PRINCIPAL)
                .map(allocation -> allocation.amount())
                .reduce(Money.zero(terms.originalPrincipal().currency()), Money::add);
        if (!prepaymentAmount.isPositive()) {
            throw new DomainRuleViolation("The approved payment has no direct principal allocation");
        }

        if (outstandingInstallments.isEmpty()) {
            throw new DomainRuleViolation("There is no outstanding installment balance to recalculate");
        }
        validateOutstandingInstallments(currentPlan, outstandingInstallments, terms.originalPrincipal().currency());
        Money outstandingPrincipal = sumOutstandingPrincipal(outstandingInstallments, terms.originalPrincipal().currency());
        if (prepaymentAmount.compareTo(outstandingPrincipal) >= 0) {
            throw new DomainRuleViolation("A principal prepayment must be lower than the outstanding principal; use the settlement workflow for a full payoff");
        }
        Money remainingPrincipal = outstandingPrincipal.subtract(prepaymentAmount);
        List<LocalDate> dueDates = outstandingInstallments.stream()
                .map(balance -> balance.dueDate().isBefore(approvedPayment.reportedPaymentDate())
                        ? approvedPayment.reportedPaymentDate() : balance.dueDate())
                .toList();
        LocalDate accrualStart = approvedPayment.reportedPaymentDate();
        boolean preservePaymentAmount = terms.capitalPrepaymentPolicy() == CapitalPrepaymentPolicy.SHORTEN_TERM;
        OutstandingInstallmentBalance firstOutstanding = outstandingInstallments.getFirst();
        Money paymentAmount = preservePaymentAmount ? firstOutstanding.agreedTotal() : null;
        Money principalComponent = preservePaymentAmount ? firstOutstanding.agreedPrincipal() : null;
        Money accruedInterest = sumAccruedComponent(outstandingInstallments, terms.originalPrincipal().currency(),
                approvedPayment.reportedPaymentDate(), OutstandingInstallmentBalance::outstandingInterest);
        Money outstandingFees = sumOutstandingComponent(outstandingInstallments, terms.originalPrincipal().currency(),
                OutstandingInstallmentBalance::outstandingFee);

        return buildPlan(paymentPlanId, terms, currentPlan.versionNumber() + 1,
                PaymentPlanReason.CAPITAL_PREPAYMENT, remainingPrincipal, accrualStart,
                dueDates, generatedAt, preservePaymentAmount, paymentAmount, principalComponent, accruedInterest,
                outstandingFees, outstandingInstallments);
    }

    private PaymentPlan buildPlan(
            PaymentPlanId paymentPlanId,
            LoanTerms terms,
            int version,
            PaymentPlanReason reason,
            Money openingPrincipal,
            LocalDate accrualStart,
            List<LocalDate> dueDates,
            java.time.Instant createdAt,
            boolean preservePaymentAmount,
            Money existingPaymentAmount,
            Money existingPrincipalAmount,
            Money carriedInterest,
            Money carriedFees,
            List<OutstandingInstallmentBalance> priorBalances) {
        if (dueDates.isEmpty()) {
            throw new DomainRuleViolation("A payment plan requires at least one due date");
        }
        List<BigDecimal> accrualFactors = accrualFactors(terms, accrualStart, dueDates);
        List<InstallmentAmounts> amounts = new ArrayList<>(switch (terms.amortizationMethod()) {
            case FIXED_PAYMENT -> fixedPaymentAmounts(openingPrincipal, accrualFactors, preservePaymentAmount, existingPaymentAmount);
            case FIXED_PRINCIPAL -> fixedPrincipalAmounts(openingPrincipal, accrualFactors, preservePaymentAmount,
                    existingPrincipalAmount);
            case INTEREST_AT_MATURITY -> interestAtMaturityAmounts(openingPrincipal, accrualFactors, preservePaymentAmount,
                    existingPrincipalAmount);
        });
        if (amounts.size() > dueDates.size()) {
            throw new DomainRuleViolation("The payment plan cannot be repaid within the contractual due dates");
        }
        applyApprovedFutureInterest(amounts, priorBalances, accrualStart);
        if (carriedInterest.isPositive() || carriedFees.isPositive()) {
            InstallmentAmounts first = amounts.getFirst();
            amounts.set(0, new InstallmentAmounts(first.principal(), first.interest().add(carriedInterest.amount()),
                    first.fee().add(carriedFees.amount())));
        }

        List<Installment> installments = new ArrayList<>(amounts.size());
        for (int index = 0; index < amounts.size(); index++) {
            InstallmentAmounts amount = amounts.get(index);
            installments.add(new Installment(
                    deterministicInstallmentId(paymentPlanId, index + 1),
                    index + 1,
                    dueDates.get(index),
                    money(amount.principal(), openingPrincipal.currency()),
                    money(amount.interest(), openingPrincipal.currency()),
                    money(amount.fee(), openingPrincipal.currency())));
        }
        return new PaymentPlan(paymentPlanId, terms.id(), version, reason, installments,
                PaymentPlanStatus.CURRENT, createdAt, null);
    }

    private List<InstallmentAmounts> fixedPaymentAmounts(
            Money openingPrincipal,
            List<BigDecimal> factors,
            boolean preservePaymentAmount,
            Money existingPaymentAmount) {
        BigDecimal periodicPayment = preservePaymentAmount
                ? existingPaymentAmount.amount()
                : calculateLevelPayment(openingPrincipal.amount(), factors);
        BigDecimal balance = openingPrincipal.amount();
        List<InstallmentAmounts> result = new ArrayList<>();
        for (BigDecimal factor : factors) {
            BigDecimal interest = round(balance.multiply(factor, MATH_CONTEXT));
            BigDecimal principal = round(periodicPayment.subtract(interest));
            if (principal.signum() <= 0) {
                throw new DomainRuleViolation("The contractual payment does not cover accrued interest");
            }
            if (principal.compareTo(balance) >= 0) {
                result.add(new InstallmentAmounts(balance, interest, zeroAmount()));
                return result;
            }
            result.add(new InstallmentAmounts(principal, interest, zeroAmount()));
            balance = balance.subtract(principal);
        }
        if (balance.signum() > 0) {
            if (preservePaymentAmount) {
                throw new DomainRuleViolation("The remaining contractual dates are insufficient to preserve the payment amount");
            }
            InstallmentAmounts last = result.removeLast();
            result.add(new InstallmentAmounts(last.principal().add(balance), last.interest(), last.fee()));
        }
        return result;
    }

    private List<InstallmentAmounts> fixedPrincipalAmounts(
            Money openingPrincipal, List<BigDecimal> factors, boolean shortenTerm, Money existingPaymentAmount) {
        BigDecimal scheduledPrincipal = shortenTerm
                ? existingPaymentAmount.amount()
                : round(openingPrincipal.amount().divide(BigDecimal.valueOf(factors.size()), MATH_CONTEXT));
        BigDecimal balance = openingPrincipal.amount();
        List<InstallmentAmounts> result = new ArrayList<>();
        for (BigDecimal factor : factors) {
            BigDecimal principal = scheduledPrincipal.min(balance);
            BigDecimal interest = round(balance.multiply(factor, MATH_CONTEXT));
            result.add(new InstallmentAmounts(principal, interest, zeroAmount()));
            balance = balance.subtract(principal);
            if (shortenTerm && balance.signum() == 0) {
                return result;
            }
        }
        if (balance.signum() > 0) {
            InstallmentAmounts last = result.removeLast();
            result.add(new InstallmentAmounts(last.principal().add(balance), last.interest(), last.fee()));
        }
        return result;
    }

    private List<InstallmentAmounts> interestAtMaturityAmounts(
            Money openingPrincipal, List<BigDecimal> factors, boolean shortenTerm, Money existingPaymentAmount) {
        List<InstallmentAmounts> principalSchedule = fixedPrincipalAmounts(
                openingPrincipal, factors, shortenTerm, existingPaymentAmount);
        BigDecimal accruedInterest = BigDecimal.ZERO.setScale(Money.SCALE);
        List<InstallmentAmounts> result = new ArrayList<>(principalSchedule.size());
        for (InstallmentAmounts installment : principalSchedule) {
            accruedInterest = accruedInterest.add(installment.interest());
            result.add(new InstallmentAmounts(installment.principal(), zeroAmount(), installment.fee()));
        }
        InstallmentAmounts finalInstallment = result.removeLast();
        result.add(new InstallmentAmounts(finalInstallment.principal(), accruedInterest, finalInstallment.fee()));
        return result;
    }

    private BigDecimal calculateLevelPayment(BigDecimal principal, List<BigDecimal> factors) {
        BigDecimal discountFactor = ONE;
        BigDecimal annuityFactor = BigDecimal.ZERO;
        for (BigDecimal factor : factors) {
            discountFactor = discountFactor.divide(ONE.add(factor), MATH_CONTEXT);
            annuityFactor = annuityFactor.add(discountFactor);
        }
        return round(principal.divide(annuityFactor, MATH_CONTEXT));
    }

    private List<BigDecimal> accrualFactors(LoanTerms terms, LocalDate accrualStart, List<LocalDate> dueDates) {
        List<BigDecimal> factors = new ArrayList<>(dueDates.size());
        LocalDate previousDate = accrualStart;
        for (LocalDate dueDate : dueDates) {
            if (dueDate.isBefore(previousDate)) {
                throw new DomainRuleViolation("A due date cannot precede the interest accrual start date");
            }
            factors.add(accrualFactor(terms, previousDate, dueDate));
            previousDate = dueDate;
        }
        return factors;
    }

    private BigDecimal accrualFactor(LoanTerms terms, LocalDate start, LocalDate end) {
        BigDecimal rate = terms.interestRate().percentage().movePointLeft(2);
        BigDecimal periods = ratePeriods(terms.ratePeriod(), terms.dayCountBasis(), start, end);
        if (rate.signum() == 0 || periods.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (terms.interestCalculationMethod() == InterestCalculationMethod.SIMPLE) {
            return rate.multiply(periods, MATH_CONTEXT);
        }
        return switch (terms.ratePeriod()) {
            case MONTHLY_NOMINAL -> rate.multiply(periods, MATH_CONTEXT);
            case ANNUAL_NOMINAL -> compound(rate.divide(TWELVE, MATH_CONTEXT), periods.multiply(TWELVE));
            case DAILY, MONTHLY_EFFECTIVE, ANNUAL_EFFECTIVE -> compound(rate, periods);
        };
    }

    private BigDecimal compound(BigDecimal rate, BigDecimal periods) {
        if (periods.stripTrailingZeros().scale() <= 0) {
            return ONE.add(rate).pow(periods.intValueExact(), MATH_CONTEXT).subtract(ONE);
        }
        double factor = Math.pow(ONE.add(rate).doubleValue(), periods.doubleValue()) - 1;
        return BigDecimal.valueOf(factor);
    }

    private BigDecimal ratePeriods(RatePeriod ratePeriod, DayCountBasis basis, LocalDate start, LocalDate end) {
        BigDecimal days = switch (basis) {
            case THIRTY_360 -> BigDecimal.valueOf(thirty360Days(start, end));
            case ACTUAL_360, ACTUAL_365 -> BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end));
        };
        return switch (ratePeriod) {
            case DAILY -> days;
            case MONTHLY_NOMINAL, MONTHLY_EFFECTIVE -> days.divide(THIRTY, MATH_CONTEXT);
            case ANNUAL_NOMINAL, ANNUAL_EFFECTIVE -> days.divide(
                    basis == DayCountBasis.ACTUAL_365 ? THREE_HUNDRED_SIXTY_FIVE : THREE_HUNDRED_SIXTY,
                    MATH_CONTEXT);
        };
    }

    private List<LocalDate> generateDueDates(LocalDate firstDueDate, PaymentScheduleRule rule, int count) {
        List<LocalDate> dueDates = new ArrayList<>(count);
        LocalDate rawDate = firstDueDate;
        dueDates.add(adjust(rawDate, rule.nonBusinessDayAdjustment()));
        if (rule.frequency() == PaymentFrequency.MONTHLY) {
            appendMonthlyDueDates(dueDates, rawDate, rule, count);
            return dueDates;
        }
        int intervalDays = switch (rule.frequency()) {
            case WEEKLY -> 7;
            case BIWEEKLY -> 14;
            case EVERY_N_DAYS -> rule.intervalDays();
            case MONTHLY -> throw new IllegalStateException("Monthly dates are handled separately");
        };
        for (int index = 1; index < count; index++) {
            rawDate = rawDate.plusDays(intervalDays);
            dueDates.add(adjust(rawDate, rule.nonBusinessDayAdjustment()));
        }
        return dueDates;
    }

    private void appendMonthlyDueDates(List<LocalDate> dueDates, LocalDate firstDueDate, PaymentScheduleRule rule, int count) {
        List<Integer> days = rule.daysOfMonth().stream().sorted(Comparator.naturalOrder()).toList();
        LocalDate month = firstDueDate.withDayOfMonth(1);
        LocalDate lastRawDate = firstDueDate;
        while (dueDates.size() < count) {
            for (Integer configuredDay : days) {
                LocalDate candidate = month.withDayOfMonth(Math.min(configuredDay, month.lengthOfMonth()));
                if (candidate.isAfter(lastRawDate)) {
                    LocalDate adjusted = adjust(candidate, rule.nonBusinessDayAdjustment());
                    if (dueDates.isEmpty() || !dueDates.getLast().equals(adjusted)) {
                        dueDates.add(adjusted);
                    }
                    lastRawDate = candidate;
                    if (dueDates.size() == count) {
                        return;
                    }
                }
            }
            month = month.plusMonths(1);
        }
    }

    private LocalDate adjust(LocalDate date, NonBusinessDayAdjustment adjustment) {
        if (adjustment == NonBusinessDayAdjustment.NO_ADJUSTMENT || businessDayCalendar.isBusinessDay(date)) {
            return date;
        }
        int direction = adjustment == NonBusinessDayAdjustment.NEXT_BUSINESS_DAY ? 1 : -1;
        LocalDate adjusted = date;
        do {
            adjusted = adjusted.plusDays(direction);
        } while (!businessDayCalendar.isBusinessDay(adjusted));
        return adjusted;
    }

    private static int thirty360Days(LocalDate start, LocalDate end) {
        int startDay = Math.min(start.getDayOfMonth(), 30);
        int endDay = Math.min(end.getDayOfMonth(), 30);
        return (end.getYear() - start.getYear()) * 360
                + (end.getMonthValue() - start.getMonthValue()) * 30
                + endDay - startDay;
    }

    private static Money sumOutstandingPrincipal(List<OutstandingInstallmentBalance> balances, String currency) {
        Money total = Money.zero(currency);
        for (OutstandingInstallmentBalance balance : balances) {
            total = total.add(balance.outstandingPrincipal());
        }
        return total;
    }

    private static Money sumAccruedComponent(
            List<OutstandingInstallmentBalance> balances,
            String currency,
            LocalDate prepaymentDate,
            java.util.function.Function<OutstandingInstallmentBalance, Money> component) {
        Money total = Money.zero(currency);
        for (OutstandingInstallmentBalance balance : balances) {
            if (!balance.dueDate().isAfter(prepaymentDate)) {
                total = total.add(component.apply(balance));
            }
        }
        return total;
    }

    private static Money sumOutstandingComponent(
            List<OutstandingInstallmentBalance> balances,
            String currency,
            java.util.function.Function<OutstandingInstallmentBalance, Money> component) {
        Money total = Money.zero(currency);
        for (OutstandingInstallmentBalance balance : balances) {
            total = total.add(component.apply(balance));
        }
        return total;
    }

    private static void applyApprovedFutureInterest(
            List<InstallmentAmounts> amounts,
            List<OutstandingInstallmentBalance> priorBalances,
            LocalDate prepaymentDate) {
        for (int index = 0; index < amounts.size() && index < priorBalances.size(); index++) {
            OutstandingInstallmentBalance balance = priorBalances.get(index);
            if (!balance.dueDate().isAfter(prepaymentDate)) {
                continue;
            }
            BigDecimal approvedInterest = balance.agreedInterest().subtract(balance.outstandingInterest()).amount();
            if (approvedInterest.signum() == 0) {
                continue;
            }
            InstallmentAmounts amount = amounts.get(index);
            BigDecimal remainingInterest = amount.interest().subtract(approvedInterest).max(BigDecimal.ZERO);
            amounts.set(index, new InstallmentAmounts(amount.principal(), remainingInterest, amount.fee()));
        }
    }

    private static void validateOutstandingInstallments(
            PaymentPlan currentPlan, List<OutstandingInstallmentBalance> balances, String currency) {
        List<OutstandingInstallmentBalance> sorted = balances.stream()
                .sorted(Comparator.comparing(OutstandingInstallmentBalance::dueDate)
                        .thenComparingInt(OutstandingInstallmentBalance::number))
                .toList();
        if (!balances.equals(sorted)) {
            throw new DomainRuleViolation("Outstanding installments must be ordered by due date and number");
        }
        java.util.Map<com.project.backend.domain.valueobject.InstallmentId, Installment> currentInstallments = currentPlan.installments().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Installment::id, installment -> installment));
        for (OutstandingInstallmentBalance balance : balances) {
            Installment source = currentInstallments.get(balance.installmentId());
            if (source == null) {
                throw new DomainRuleViolation("The outstanding balance does not belong to the current payment plan");
            }
            if (!source.agreedPrincipal().equals(balance.agreedPrincipal())
                    || !source.agreedInterest().equals(balance.agreedInterest())
                    || !source.agreedFee().equals(balance.agreedFee())) {
                throw new DomainRuleViolation("The outstanding balance does not match its contractual installment");
            }
            if (!currency.equals(balance.outstandingPrincipal().currency())) {
                throw new DomainRuleViolation("The outstanding balances must use the loan currency");
            }
        }
    }

    private static Money money(BigDecimal amount, String currency) {
        return new Money(round(amount), currency);
    }

    private static BigDecimal round(BigDecimal amount) {
        return amount.setScale(Money.SCALE, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(Money.SCALE);
    }

    private static LocalDate toLocalDate(java.time.Instant instant, ZoneId timeZone) {
        return instant.atZone(timeZone).toLocalDate();
    }

    private static InstallmentId deterministicInstallmentId(PaymentPlanId planId, int number) {
        UUID value = UUID.nameUUIDFromBytes((planId.value() + ":" + number).getBytes(StandardCharsets.UTF_8));
        return new InstallmentId(value);
    }

    private record InstallmentAmounts(BigDecimal principal, BigDecimal interest, BigDecimal fee) { }
}
