package com.project.backend.domain.repository;

import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.LoanId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto del aggregate ReportedPayment. */
public interface ReportedPaymentRepository extends Repository<ReportedPayment, ReportedPaymentId> {

    List<ReportedPayment> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status);

    Optional<ReportedPayment> findByIdForUpdate(ReportedPaymentId id);

    Optional<ReportedPayment> findByIdempotencyKey(UUID idempotencyKey);
}
