package com.project.backend.application.query;

import com.project.backend.application.dto.Query;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.LoanId;

import java.util.Objects;

public record ListPendingPaymentsQuery(UserAccountId lenderAccountId, LoanId loanId) implements Query {
    public ListPendingPaymentsQuery {
        Objects.requireNonNull(lenderAccountId, "La account lender es obligatoria");
        Objects.requireNonNull(loanId, "The loan is required");
    }
}
