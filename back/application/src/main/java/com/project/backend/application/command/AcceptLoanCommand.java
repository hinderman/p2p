package com.project.backend.application.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.LoanAccepted;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.LoanId;

import java.util.Objects;

public record AcceptLoanCommand(UserAccountId payerAccountId, LoanId loanId) implements Command<LoanAccepted> {
    public AcceptLoanCommand {
        Objects.requireNonNull(payerAccountId, "The payer account is required");
        Objects.requireNonNull(loanId, "The loan is required");
    }
}
