package com.project.backend.application.command;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.dto.ClaimedAccountVerification;
import com.project.backend.application.exception.VerificationInvalidException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AccountVerificationPort;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.PersonStatus;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.UserAccountRepository;

import java.time.Instant;
import java.util.Objects;

/**
 * Activates the account whose address was just proven, and signs the person in.
 *
 * <p>Returning a session is deliberate: the person has just proven both that
 * they control the address and, by having set it moments earlier, that they know
 * the password. Sending them back to a login form would add friction without
 * adding a check.
 */
public final class VerifyAccountEmailHandler
        implements CommandHandler<VerifyAccountEmailCommand, AuthenticatedSession> {
    private final AccountVerificationPort verifications;
    private final UserAccountRepository accounts;
    private final PersonRepository people;
    private final AuthenticationTokenIssuerPort tokenIssuer;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public VerifyAccountEmailHandler(
            AccountVerificationPort verifications,
            UserAccountRepository accounts,
            PersonRepository people,
            AuthenticationTokenIssuerPort tokenIssuer,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.verifications = Objects.requireNonNull(verifications, "The account verification port is required");
        this.accounts = Objects.requireNonNull(accounts, "The user account repository is required");
        this.people = Objects.requireNonNull(people, "The person repository is required");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "The authentication token issuer is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
        this.outbox = Objects.requireNonNull(outbox, "The outbox port is required");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "The unit of work is required");
    }

    @Override
    public Class<VerifyAccountEmailCommand> requestType() {
        return VerifyAccountEmailCommand.class;
    }

    @Override
    public AuthenticatedSession execute(VerifyAccountEmailCommand command) {
        return unitOfWork.execute(() -> verify(command));
    }

    private AuthenticatedSession verify(VerifyAccountEmailCommand command) {
        Instant now = clock.now();
        ClaimedAccountVerification claimed = verifications
                .claim(command.verificationToken(), AccountVerificationPurpose.EMAIL_VERIFICATION, now)
                .orElseThrow(VerificationInvalidException::new);

        UserAccount account = accounts.findById(claimed.userAccountId())
                .orElseThrow(VerificationInvalidException::new);
        if (account.status() != UserAccountStatus.PENDING_VERIFICATION) {
            throw new VerificationInvalidException();
        }

        account.activate(now);
        accounts.save(account);
        activatePerson(account);
        verifications.markPrimaryEmailVerified(account.id(), now);

        outbox.enqueue(account.domainEvents());
        account.pullEvents();
        return tokenIssuer.issue(account, now);
    }

    private void activatePerson(UserAccount account) {
        Person person = people.findById(account.personId())
                .orElseThrow(VerificationInvalidException::new);
        if (person.status() == PersonStatus.PENDING) {
            person.activate();
            people.save(person);
        }
    }
}
