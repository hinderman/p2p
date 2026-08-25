package com.project.backend.application.command;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.InvitationInvalidException;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.LoanInvitationPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.PasswordHashingPort;
import com.project.backend.application.port.out.PasswordVerifierPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.PersonStatus;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.service.PaymentPlanGenerator;
import com.project.backend.domain.valueobject.PaymentPlanId;
import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Completes the invited payer's identity setup and accepts the exact invited loan atomically. */
public final class CompletePayerOnboardingHandler
        implements CommandHandler<CompletePayerOnboardingCommand, AuthenticatedSession> {
    private final LoanInvitationPort invitations;
    private final PersonRepository people;
    private final UserAccountRepository accounts;
    private final LoanRepository loans;
    private final PasswordHashingPort passwordHasher;
    private final PasswordVerifierPort passwordVerifier;
    private final PaymentPlanGenerator paymentPlans;
    private final AuthenticationTokenIssuerPort tokenIssuer;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public CompletePayerOnboardingHandler(
            LoanInvitationPort invitations,
            PersonRepository people,
            UserAccountRepository accounts,
            LoanRepository loans,
            PasswordHashingPort passwordHasher,
            PasswordVerifierPort passwordVerifier,
            PaymentPlanGenerator paymentPlans,
            AuthenticationTokenIssuerPort tokenIssuer,
            UuidGeneratorPort uuids,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.invitations = Objects.requireNonNull(invitations, "The loan invitation port is required");
        this.people = Objects.requireNonNull(people, "The person repository is required");
        this.accounts = Objects.requireNonNull(accounts, "The user account repository is required");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "The password hashing port is required");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "The password verifier port is required");
        this.paymentPlans = Objects.requireNonNull(paymentPlans, "The payment-plan generator is required");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "The authentication token issuer is required");
        this.uuids = Objects.requireNonNull(uuids, "The UUID generator is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
        this.outbox = Objects.requireNonNull(outbox, "The outbox port is required");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "The unit of work is required");
    }

    @Override
    public Class<CompletePayerOnboardingCommand> requestType() {
        return CompletePayerOnboardingCommand.class;
    }

    @Override
    public AuthenticatedSession execute(CompletePayerOnboardingCommand command) {
        char[] password = command.password();
        try {
            return unitOfWork.execute(() -> complete(command, password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private AuthenticatedSession complete(CompletePayerOnboardingCommand command, char[] password) {
        Instant now = clock.now();
        var invitation = invitations.claimForPayerOnboarding(command.invitationToken(), now)
                .orElseThrow(InvitationInvalidException::new);
        Loan loan = loans.findById(invitation.loanId())
                .orElseThrow(() -> new ResourceNotFoundException("The invited loan does not exist"));
        var terms = loan.terms().stream()
                .filter(term -> term.id().equals(invitation.loanTermId()) && term.status() == LoanTermStatus.PROPOSED)
                .findFirst()
                .orElseThrow(InvitationInvalidException::new);
        Person payer = people.findByEmail(invitation.recipientEmail())
                .orElseThrow(InvitationInvalidException::new);
        if (!loan.payerPersonId().equals(payer.id()) || payer.status() == PersonStatus.BLOCKED || payer.status() == PersonStatus.INACTIVE) {
            throw new InvitationInvalidException();
        }

        UserAccount account = resolvePayerAccount(payer, invitation.recipientEmail(), password, now);
        if (payer.status() == PersonStatus.PENDING) {
            payer.activate();
            people.save(payer);
        }
        var paymentPlan = paymentPlans.generateInitial(new PaymentPlanId(uuids.nextUuid()), terms, now);
        loan.acceptTerms(terms.id(), payer.id(), account.id(), paymentPlan, now);
        accounts.save(account);
        loans.save(loan);
        invitations.recordPayerAcceptance(invitation, account.id(), command.sourceIp(), command.userAgent(), now);

        List<com.project.backend.domain.event.DomainEvent> events = new ArrayList<>(account.domainEvents());
        events.addAll(loan.domainEvents());
        outbox.enqueue(events);
        account.pullEvents();
        loan.pullEvents();
        return tokenIssuer.issue(account, now);
    }

    private UserAccount resolvePayerAccount(Person payer, com.project.backend.domain.valueobject.EmailAddress email, char[] password, Instant now) {
        var existing = accounts.findByEmail(email);
        if (existing.isEmpty()) {
            UserAccount account = UserAccount.createPending(new UserAccountId(uuids.nextUuid()), payer.id(),
                    passwordHasher.hash(password), java.util.Set.of(UserRole.PAYER), now);
            account.activate(now);
            return account;
        }
        UserAccount account = existing.get();
        if (!account.personId().equals(payer.id())
                || (account.status() != UserAccountStatus.ACTIVE && account.status() != UserAccountStatus.PENDING_VERIFICATION)) {
            throw new InvitationInvalidException();
        }
        if (account.status() == UserAccountStatus.ACTIVE && !passwordVerifier.matches(password, account.passwordHash())) {
            throw new InvitationInvalidException();
        }
        if (account.status() == UserAccountStatus.PENDING_VERIFICATION) {
            account.changePassword(passwordHasher.hash(password), now);
            account.activate(now);
        }
        account.assignRole(UserRole.PAYER);
        return account;
    }
}
