package com.project.backend.application.port.out;

import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.OutstandingInstallmentBalance;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.LoanId;

import java.util.List;

/**
 * Transactional financial invariants that require persisted payment history.
 * The loan lock serializes approval/reversal operations for one loan.
 */
public interface PaymentAllocationValidationPort {
    void lockLoanForFinancialChange(LoanId loanId);

    void validateApproval(Loan loan, ReportedPayment payment);

    /** Snapshot of the unpaid component balance in the current plan, under the loan lock. */
    List<OutstandingInstallmentBalance> outstandingCurrentInstallments(Loan loan);
}
