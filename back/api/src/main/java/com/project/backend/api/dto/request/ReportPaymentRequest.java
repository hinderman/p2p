package com.project.backend.api.dto.request;

import com.project.backend.domain.payment.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ReportPaymentRequest(
        @NotNull PaymentType paymentType,
        @NotNull @Valid MoneyRequest reportedAmount,
        @NotNull LocalDate reportedPaymentDate,
        @Size(max = 150) String externalReference,
        @NotEmpty List<@Valid PaymentProofRequest> proofs) {
}
