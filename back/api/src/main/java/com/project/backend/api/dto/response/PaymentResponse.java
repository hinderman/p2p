package com.project.backend.api.dto.response;

import com.project.backend.domain.payment.ReportedPaymentStatus;

import java.util.UUID;

public record PaymentResponse(UUID reportedPaymentId, ReportedPaymentStatus status) {
}
