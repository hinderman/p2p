package com.project.backend.application.port.out;

import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PageRequest;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.LoanId;

public interface PaymentReadModelPort {
    /**
     * Returns one page of the projection. The slice is resolved by the store, so
     * neither this layer nor the client ever holds the complete result set.
     */
    Page<PaymentSummary> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status, PageRequest pageRequest);
}
