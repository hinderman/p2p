package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.domain.payment.PaymentAllocation;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.ReportedPaymentId;

import java.util.List;
import java.util.Objects;

public record ApprovePaymentCommand(
        UserAccountId lenderAccountId,
        ReportedPaymentId reportedPaymentId,
        Money validatedAmount,
        List<PaymentAllocation> allocations) implements Command<PaymentProcessed> {
    public ApprovePaymentCommand {
        Objects.requireNonNull(lenderAccountId, "La account lender es obligatoria");
        Objects.requireNonNull(reportedPaymentId, "El payment es obligatorio");
        Objects.requireNonNull(validatedAmount, "El amount validado es obligatorio");
        allocations = List.copyOf(Objects.requireNonNull(allocations, "Las allocations son obligatorias"));
    }
}
