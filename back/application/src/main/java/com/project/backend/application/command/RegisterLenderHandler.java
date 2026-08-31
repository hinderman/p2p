package com.project.backend.application.command;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.IssuedAccountVerification;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AccountVerificationPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.PasswordHashingPort;
import com.project.backend.application.port.out.RegistrationRateLimitPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.domain.identity.PasswordPolicy;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Opens a lender account without telling the caller whether the address was
 * already registered.
 *
 * <p>Every branch returns the same empty result, so the endpoint cannot be used
 * to harvest which addresses hold accounts. The address owner is still informed:
 * an unregistered address receives a verification link, and a registered one
 * receives a notice that someone attempted to register with it.
 *
 * <p>An account still pending verification has never been usable, so a repeated
 * registration replaces its credentials. That lets someone who mistyped their
 * password simply register again, and gains an attacker nothing, because
 * activation still requires the link sent to the mailbox.
 */
public final class RegisterLenderHandler implements CommandHandler<RegisterLenderCommand, Void> {
    private final PersonRepository people;
    private final UserAccountRepository accounts;
    private final AccountVerificationPort verifications;
    private final PasswordHashingPort passwordHasher;
    private final RegistrationRateLimitPort rateLimit;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public RegisterLenderHandler(
            PersonRepository people,
            UserAccountRepository accounts,
            AccountVerificationPort verifications,
            PasswordHashingPort passwordHasher,
            RegistrationRateLimitPort rateLimit,
            UuidGeneratorPort uuids,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.people = Objects.requireNonNull(people, "The person repository is required");
        this.accounts = Objects.requireNonNull(accounts, "The user account repository is required");
        this.verifications = Objects.requireNonNull(verifications, "The account verification port is required");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "The password hashing port is required");
        this.rateLimit = Objects.requireNonNull(rateLimit, "The registration rate-limit port is required");
        this.uuids = Objects.requireNonNull(uuids, "The UUID generator is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
        this.outbox = Objects.requireNonNull(outbox, "The outbox port is required");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "The unit of work is required");
    }

    @Override
    public Class<RegisterLenderCommand> requestType() {
        return RegisterLenderCommand.class;
    }

    @Override
    public Void execute(RegisterLenderCommand command) {
        char[] password = command.password();
        try {
            return unitOfWork.execute(() -> register(command, password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private Void register(RegisterLenderCommand command, char[] password) {
        EmailAddress email = command.email();
        Instant now = clock.now();
        rateLimit.checkAllowed(email, command.sourceIp(), now);
        rateLimit.recordAttempt(email, command.sourceIp(), now);
        PasswordPolicy.validate(password, email);

        Optional<UserAccount> registered = accounts.findByEmail(email);
        if (registered.isPresent()) {
            reRegister(registered.get(), email, password, now);
            return null;
        }

        Person person = people.findByEmail(email)
                .map(existing -> {
                    existing.identify(command.firstName(), command.lastName());
                    return people.save(existing);
                })
                .orElseGet(() -> people.save(Person.register(new PersonId(uuids.nextUuid()), email,
                        command.firstName(), command.lastName(), now)));

        UserAccount account = UserAccount.createPending(new UserAccountId(uuids.nextUuid()), person.id(),
                passwordHasher.hash(password), Set.of(UserRole.LENDER), now);
        accounts.save(account);
        requestVerificationEmail(account.id(), email, now);
        return null;
    }

    private void reRegister(UserAccount account, EmailAddress email, char[] password, Instant now) {
        if (account.status() != UserAccountStatus.PENDING_VERIFICATION) {
            outbox.enqueueEmail(OutboundEmailMessage.existingAccountNotice(account.id(), email), now);
            return;
        }
        account.changePassword(passwordHasher.hash(password), now);
        account.assignRole(UserRole.LENDER);
        accounts.save(account);
        requestVerificationEmail(account.id(), email, now);
    }

    private void requestVerificationEmail(UserAccountId accountId, EmailAddress email, Instant now) {
        IssuedAccountVerification issued = verifications.issue(
                accountId, AccountVerificationPurpose.EMAIL_VERIFICATION, now);
        outbox.enqueueEmail(OutboundEmailMessage.accountEmailVerification(
                issued.verificationId(), email, issued.rawToken()), now);
    }
}
