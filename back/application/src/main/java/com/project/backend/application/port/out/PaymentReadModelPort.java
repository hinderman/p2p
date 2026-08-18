package com.project.backend.application.port.out;

import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.LoanId;

import java.util.List;

public interface PaymentReadModelPort {
    List<PaymentSummary> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status);
}
