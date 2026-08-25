package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.ReportedPaymentId;

import java.util.Objects;

public record ReversePaymentCommand(UserAccountId lenderAccountId, ReportedPaymentId reportedPaymentId, String reason)
        implements Command<PaymentProcessed> {
    public ReversePaymentCommand {
        Objects.requireNonNull(lenderAccountId, "La account lender es obligatoria");
        Objects.requireNonNull(reportedPaymentId, "El payment es obligatorio");
    }
}
