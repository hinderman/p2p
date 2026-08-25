package com.project.backend.application.command;

import com.project.backend.application.dto.LoanAccepted;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.service.PaymentPlanGenerator;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.time.Instant;
import java.util.Objects;

public final class AcceptLoanHandler implements CommandHandler<AcceptLoanCommand, LoanAccepted> {
    private final ApplicationAuthorizer authorizer;
    private final LoanRepository loans;
    private final PaymentPlanGenerator paymentPlanGenerator;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public AcceptLoanHandler(
            ApplicationAuthorizer authorizer,
            LoanRepository loans,
            PaymentPlanGenerator paymentPlanGenerator,
            UuidGeneratorPort uuids,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.paymentPlanGenerator = Objects.requireNonNull(paymentPlanGenerator, "El generador de paymentPlan es obligatorio");
        this.uuids = Objects.requireNonNull(uuids, "El generador de UUID es obligatorio");
        this.clock = Objects.requireNonNull(clock, "El clock es obligatorio");
        this.outbox = Objects.requireNonNull(outbox, "El outbox es obligatorio");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "La unidad de trabajo es obligatoria");
    }

    @Override
    public Class<AcceptLoanCommand> requestType() {
        return AcceptLoanCommand.class;
    }

    @Override
    public LoanAccepted execute(AcceptLoanCommand command) {
        return unitOfWork.execute(() -> {
            var payer = authorizer.requireActiveAccountWithRole(command.payerAccountId(), UserRole.PAYER);
            Loan loan = loans.findById(command.loanId())
                    .orElseThrow(() -> new ResourceNotFoundException("The loan does not exist"));
            authorizer.requirePayerOwnership(payer, loan);
            LoanTerms proposedTerms = loan.terms().stream()
                    .filter(term -> term.status() == LoanTermStatus.PROPOSED)
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No pending proposal exists for the loan"));
            Instant now = clock.now();
            var paymentPlan = paymentPlanGenerator.generateInitial(new PaymentPlanId(uuids.nextUuid()), proposedTerms, now);
            loan.acceptTerms(proposedTerms.id(), payer.personId(), payer.id(), paymentPlan, now);
            loans.save(loan);
            outbox.enqueue(loan.domainEvents());
            loan.pullEvents();
            return new LoanAccepted(loan.id(), loan.status());
        });
    }
}
