package com.project.backend.application.query;

import com.project.backend.application.dto.LoanDetail;
import com.project.backend.application.dto.Query;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;

public record GetLoanDetailQuery(UserAccountId accountId, LoanId loanId) implements Query<LoanDetail> {
    public GetLoanDetailQuery {
        Objects.requireNonNull(accountId, "The account is required");
        Objects.requireNonNull(loanId, "The loan is required");
    }
}
