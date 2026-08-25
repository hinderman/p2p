package com.project.backend.application.query;

import com.project.backend.application.dto.Query;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;

public record ListLenderLoansQuery(UserAccountId lenderAccountId) implements Query<java.util.List<LoanSummary>> {
    public ListLenderLoansQuery {
        Objects.requireNonNull(lenderAccountId, "La account lender es obligatoria");
    }
}
