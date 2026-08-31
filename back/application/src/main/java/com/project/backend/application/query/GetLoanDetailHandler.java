package com.project.backend.application.query;

import com.project.backend.application.dto.LoanDetail;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.port.out.LoanReadModelPort;
import com.project.backend.application.security.ApplicationAuthorizer;

import java.util.Objects;

public final class GetLoanDetailHandler implements QueryHandler<GetLoanDetailQuery, LoanDetail> {
    private final ApplicationAuthorizer authorizer;
    private final LoanReadModelPort readModel;

    public GetLoanDetailHandler(ApplicationAuthorizer authorizer, LoanReadModelPort readModel) {
        this.authorizer = Objects.requireNonNull(authorizer, "The authorizer is required");
        this.readModel = Objects.requireNonNull(readModel, "The loan read model is required");
    }

    @Override
    public Class<GetLoanDetailQuery> requestType() { return GetLoanDetailQuery.class; }

    @Override
    public LoanDetail execute(GetLoanDetailQuery query) {
        var account = authorizer.requireActiveAccount(query.accountId());
        return readModel.findDetail(query.loanId(), account.personId())
                .orElseThrow(() -> new ResourceNotFoundException("The loan does not exist"));
    }
}
