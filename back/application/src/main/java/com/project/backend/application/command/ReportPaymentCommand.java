package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.PaymentRegistered;
import com.project.backend.domain.payment.PaymentProof;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.LoanId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReportPaymentCommand(
        UserAccountId payerAccountId,
        LoanId loanId,
        PaymentType paymentType,
        Money reportedAmount,
        LocalDate reportedPaymentDate,
        String externalReference,
        UUID idempotencyKey,
        List<PaymentProof> proofs) implements Command<PaymentRegistered> {
    public ReportPaymentCommand {
        Objects.requireNonNull(payerAccountId, "The payer account is required");
        Objects.requireNonNull(loanId, "The loan is required");
        Objects.requireNonNull(paymentType, "El type de payment es obligatorio");
        Objects.requireNonNull(reportedAmount, "El amount es obligatorio");
        Objects.requireNonNull(reportedPaymentDate, "La fecha de payment es obligatoria");
        Objects.requireNonNull(idempotencyKey, "La clave de idempotencia es obligatoria");
        proofs = List.copyOf(Objects.requireNonNull(proofs, "Los proofs son obligatorios"));
    }
}
