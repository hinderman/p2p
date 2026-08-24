package com.project.backend.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ApprovePaymentRequest(
        @NotNull @Valid MoneyRequest validatedAmount,
        @NotEmpty List<@Valid PaymentAllocationRequest> allocations) {
}
