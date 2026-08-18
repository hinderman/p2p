package com.project.backend.application.query;

import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.port.out.PaymentReadModelPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.repository.LoanRepository;

import java.util.List;
import java.util.Objects;

/** CQRS query that authorizes access to the aggregate before using the projection. */
public final class ListPendingPaymentsHandler
        implements QueryHandler<ListPendingPaymentsQuery, List<PaymentSummary>> {
    private final ApplicationAuthorizer authorizer;
    private final LoanRepository loans;
    private final PaymentReadModelPort readModel;

    public ListPendingPaymentsHandler(
            ApplicationAuthorizer authorizer, LoanRepository loans, PaymentReadModelPort readModel) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.readModel = Objects.requireNonNull(readModel, "El puerto de lectura es obligatorio");
    }

    @Override
    public List<PaymentSummary> execute(ListPendingPaymentsQuery query) {
        var account = authorizer.requireActiveAccountWithRole(query.lenderAccountId(), UserRole.LENDER);
        var loan = loans.findById(query.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("The loan does not exist"));
        authorizer.requireLenderOwnership(account, loan);
        return List.copyOf(readModel.findByLoanAndStatus(query.loanId(), ReportedPaymentStatus.PENDING_REVIEW));
    }
}
