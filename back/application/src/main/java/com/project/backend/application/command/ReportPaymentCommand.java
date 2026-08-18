package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.domain.payment.PaymentProof;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.LoanId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record ReportPaymentCommand(
        UserAccountId payerAccountId,
        LoanId loanId,
        PaymentType paymentType,
        Money reportedAmount,
        LocalDate reportedPaymentDate,
        String externalReference,
        List<PaymentProof> proofs) implements Command {
    public ReportPaymentCommand {
        Objects.requireNonNull(payerAccountId, "The payer account is required");
        Objects.requireNonNull(loanId, "The loan is required");
        Objects.requireNonNull(paymentType, "El type de payment es obligatorio");
        Objects.requireNonNull(reportedAmount, "El amount es obligatorio");
        Objects.requireNonNull(reportedPaymentDate, "La fecha de payment es obligatoria");
        proofs = List.copyOf(Objects.requireNonNull(proofs, "Los proofs son obligatorios"));
    }
}
