package com.project.backend.application.query;

import com.project.backend.application.dto.PageRequest;
import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.dto.Query;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.LoanId;

import java.util.Objects;

public record ListPendingPaymentsQuery(UserAccountId lenderAccountId, LoanId loanId, PageRequest page)
        implements Query<Page<PaymentSummary>> {
    public ListPendingPaymentsQuery {
        Objects.requireNonNull(lenderAccountId, "La account lender es obligatoria");
        Objects.requireNonNull(loanId, "The loan is required");
        Objects.requireNonNull(page, "The page request is required");
    }
}
