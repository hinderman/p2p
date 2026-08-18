package com.project.backend.application.dto;

import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.valueobject.ReportedPaymentId;

public record PaymentProcessed(ReportedPaymentId reportedPaymentId, ReportedPaymentStatus status) {
}
