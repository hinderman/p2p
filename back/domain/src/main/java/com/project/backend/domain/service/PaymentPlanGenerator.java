package com.project.backend.domain.service;

import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.PaymentPlan;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.time.Instant;

/**
 * Domain financial policy. Its implementation will be plain Java, not a
 * stored procedure or an infrastructure adapter.
 */
public interface PaymentPlanGenerator extends DomainService {

    PaymentPlan generateInitial(PaymentPlanId paymentPlanId, LoanTerms terms, Instant generatedAt);

    PaymentPlan recalculateAfterCapitalPrepayment(
            PaymentPlanId paymentPlanId, Loan loan, ReportedPayment approvedPayment, Instant generatedAt);
}
