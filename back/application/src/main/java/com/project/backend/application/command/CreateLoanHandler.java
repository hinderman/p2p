package com.project.backend.application.command;

import com.project.backend.application.dto.LoanCreated;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.application.port.out.LoanInvitationPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.event.DomainEvent;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.LoanId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Creates the proposal and schedules an invitation without disclosing whether the email already has an account. */
public final class CreateLoanHandler implements CommandHandler<CreateLoanCommand, LoanCreated> {
    private final ApplicationAuthorizer authorizer;
    private final PersonRepository people;
    private final LoanRepository loans;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;
    private final LoanInvitationPort invitations;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public CreateLoanHandler(
            ApplicationAuthorizer authorizer,
            PersonRepository people,
            LoanRepository loans,
            UuidGeneratorPort uuids,
            ClockPort clock,
            LoanInvitationPort invitations,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.people = Objects.requireNonNull(people, "El repositorio de people es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.uuids = Objects.requireNonNull(uuids, "El generador de UUID es obligatorio");
        this.clock = Objects.requireNonNull(clock, "El clock es obligatorio");
        this.invitations = Objects.requireNonNull(invitations, "El puerto de invitations es obligatorio");
        this.outbox = Objects.requireNonNull(outbox, "El outbox es obligatorio");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "La unidad de trabajo es obligatoria");
    }

    @Override
    public LoanCreated execute(CreateLoanCommand command) {
        return unitOfWork.execute(() -> {
            var lender = authorizer.requireActiveAccountWithRole(command.lenderAccountId(), UserRole.LENDER);
            Instant now = clock.now();
            Person payer = people.findByEmail(command.payerEmail())
                    .orElseGet(() -> {
                        Person newPerson = Person.createPending(new PersonId(uuids.nextUuid()), command.payerEmail(), now);
                        return people.save(newPerson);
                    });
            LoanTerms terms = new LoanTerms(
                    new LoanTermId(uuids.nextUuid()),
                    1,
                    command.originalPrincipal(),
                    command.interestRate(),
                    command.ratePeriod(),
                    command.interestCalculationMethod(),
                    command.dayCountBasis(),
                    command.amortizationMethod(),
                    command.capitalPrepaymentPolicy(),
                    command.installmentCount(),
                    command.firstDueDate(),
                    command.timeZone(),
                    command.paymentScheduleRule(),
                    LoanTermStatus.PROPOSED);
            Loan loan = Loan.create(
                    new LoanId(uuids.nextUuid()), lender.personId(), payer.id(), terms, now);
            loans.save(loan);
            invitations.scheduleInvitation(loan.id(), terms.id(), command.payerEmail());
            persistEvents(loan.domainEvents(), loan);
            return new LoanCreated(loan.id(), loan.status());
        });
    }

    private void persistEvents(List<DomainEvent> domainEvents, Loan loan) {
        outbox.enqueue(domainEvents);
        loan.pullEvents();
    }
}
