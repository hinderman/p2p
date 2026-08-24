package com.project.backend.api.dto.request;

import com.project.backend.domain.payment.PaymentAllocationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentAllocationRequest(
        UUID installmentId,
        @NotNull PaymentAllocationType type,
        @NotNull @Valid MoneyRequest amount) {
}
