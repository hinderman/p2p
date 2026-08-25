package com.project.backend.application.query;

import com.project.backend.application.dto.Query;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;

public record ListPayerLoansQuery(UserAccountId payerAccountId) implements Query<java.util.List<LoanSummary>> {
    public ListPayerLoansQuery {
        Objects.requireNonNull(payerAccountId, "The payer account is required");
    }
}
