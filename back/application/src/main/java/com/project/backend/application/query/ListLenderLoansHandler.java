package com.project.backend.application.query;

import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.port.out.LoanReadModelPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;

import java.util.List;
import java.util.Objects;

public final class ListLenderLoansHandler
        implements QueryHandler<ListLenderLoansQuery, List<LoanSummary>> {
    private final ApplicationAuthorizer authorizer;
    private final LoanReadModelPort readModel;

    public ListLenderLoansHandler(ApplicationAuthorizer authorizer, LoanReadModelPort readModel) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.readModel = Objects.requireNonNull(readModel, "El puerto de lectura es obligatorio");
    }

    @Override
    public List<LoanSummary> execute(ListLenderLoansQuery query) {
        var account = authorizer.requireActiveAccountWithRole(query.lenderAccountId(), UserRole.LENDER);
        return List.copyOf(readModel.findByLender(account.personId()));
    }
}
