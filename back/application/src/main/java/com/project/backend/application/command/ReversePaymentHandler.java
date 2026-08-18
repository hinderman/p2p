package com.project.backend.application.command;

import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.repository.LoanRepository;

import java.util.Objects;

public final class ReversePaymentHandler implements CommandHandler<ReversePaymentCommand, PaymentProcessed> {
    private final ApplicationAuthorizer authorizer;
    private final ReportedPaymentRepository payments;
    private final LoanRepository loans;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public ReversePaymentHandler(
            ApplicationAuthorizer authorizer,
            ReportedPaymentRepository payments,
            LoanRepository loans,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.payments = Objects.requireNonNull(payments, "El repositorio de payments es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.clock = Objects.requireNonNull(clock, "El clock es obligatorio");
        this.outbox = Objects.requireNonNull(outbox, "El outbox es obligatorio");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "La unidad de trabajo es obligatoria");
    }

    @Override
    public PaymentProcessed execute(ReversePaymentCommand command) {
        return unitOfWork.execute(() -> {
            var lender = authorizer.requireActiveAccountWithRole(command.lenderAccountId(), UserRole.LENDER);
            var payment = payments.findById(command.reportedPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("El payment no exists"));
            var loan = loans.findById(payment.loanId())
                    .orElseThrow(() -> new ResourceNotFoundException("The payment loan does not exist"));
            authorizer.requireLenderOwnership(lender, loan);
            payment.reverse(lender.id(), command.reason(), clock.now());
            payments.save(payment);
            outbox.enqueue(payment.domainEvents());
            payment.pullEvents();
            return new PaymentProcessed(payment.id(), payment.status());
        });
    }
}
