package com.project.backend.application.command;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.ClaimedAccountVerification;
import com.project.backend.application.dto.IssuedAccountVerification;
import com.project.backend.application.dto.OutboundEmailKind;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.port.out.AccountVerificationPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.RegistrationRateLimitPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.domain.event.DomainEvent;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterLenderHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final EmailAddress EMAIL = new EmailAddress("carolina@example.com");
    private static final String PASSWORD = "mesa verde caliente";

    private final InMemoryPeople people = new InMemoryPeople();
    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final RecordingVerifications verifications = new RecordingVerifications();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final CountingRateLimit rateLimit = new CountingRateLimit();

    private RegisterLenderHandler handler() {
        return new RegisterLenderHandler(people, accounts, verifications,
                password -> new PasswordHash("argon2id:" + new String(password)), rateLimit,
                UUID::randomUUID, () -> NOW, outbox, directUnitOfWork());
    }

    @Test
    void creates_a_pending_lender_account_and_sends_a_verification_link() {
        handler().execute(command(PASSWORD));

        Person person = people.byEmail.get(EMAIL);
        assertNotNull(person);
        assertEquals("Carolina", person.firstName());

        UserAccount account = accounts.byEmail.get(EMAIL);
        assertNotNull(account);
        assertEquals(UserAccountStatus.PENDING_VERIFICATION, account.status());
        assertEquals(Set.of(UserRole.LENDER), account.roles());
        assertEquals(List.of(account.id()), verifications.issuedFor);
        assertEquals(List.of(OutboundEmailKind.ACCOUNT_EMAIL_VERIFICATION), outbox.kinds());
    }

    /** The same human may already exist as an invited payer; registering must reuse that person. */
    @Test
    void reuses_a_person_that_was_created_by_a_loan_invitation() {
        Person invited = Person.createPending(new PersonId(UUID.randomUUID()), EMAIL, NOW.minusSeconds(600));
        people.byEmail.put(EMAIL, invited);

        handler().execute(command(PASSWORD));

        assertEquals(1, people.byEmail.size());
        assertEquals(invited.id(), people.byEmail.get(EMAIL).id());
        assertEquals("Carolina", people.byEmail.get(EMAIL).firstName());
        assertEquals(invited.id(), accounts.byEmail.get(EMAIL).personId());
    }

    /**
     * The endpoint must not become an address oracle: an address that already has
     * an active account produces the same empty result, no account change, and a
     * notice to the real owner rather than a verification link.
     */
    @Test
    void does_not_disclose_or_alter_an_address_that_already_has_an_active_account() {
        Person existing = Person.register(new PersonId(UUID.randomUUID()), EMAIL, "Carolina", "Restrepo", NOW.minusSeconds(600));
        UserAccount active = UserAccount.createPending(new UserAccountId(UUID.randomUUID()), existing.id(),
                new PasswordHash("argon2id:original"), Set.of(UserRole.PAYER), NOW.minusSeconds(600));
        active.activate(NOW.minusSeconds(500));
        people.byEmail.put(EMAIL, existing);
        accounts.byEmail.put(EMAIL, active);

        handler().execute(command(PASSWORD));

        assertEquals(new PasswordHash("argon2id:original"), accounts.byEmail.get(EMAIL).passwordHash());
        assertEquals(Set.of(UserRole.PAYER), accounts.byEmail.get(EMAIL).roles());
        assertEquals(List.of(OutboundEmailKind.EXISTING_ACCOUNT_NOTICE), outbox.kinds());
        assertTrue(verifications.issuedFor.isEmpty());
        assertNull(outbox.messages.getFirst().rawToken());
    }

    /**
     * An account still pending verification has never been usable, so registering
     * again replaces its credentials — that is how someone who mistyped a password
     * recovers, and it hands an attacker nothing, because the link still goes to
     * the mailbox.
     */
    @Test
    void replaces_the_credentials_of_an_account_that_was_never_verified() {
        Person existing = Person.register(new PersonId(UUID.randomUUID()), EMAIL, "Carolina", "Restrepo", NOW.minusSeconds(600));
        UserAccount pending = UserAccount.createPending(new UserAccountId(UUID.randomUUID()), existing.id(),
                new PasswordHash("argon2id:mistyped"), Set.of(UserRole.LENDER), NOW.minusSeconds(600));
        people.byEmail.put(EMAIL, existing);
        accounts.byEmail.put(EMAIL, pending);

        handler().execute(command("otra clave larga"));

        assertEquals(new PasswordHash("argon2id:otra clave larga"), accounts.byEmail.get(EMAIL).passwordHash());
        assertEquals(UserAccountStatus.PENDING_VERIFICATION, accounts.byEmail.get(EMAIL).status());
        assertEquals(List.of(pending.id()), verifications.issuedFor);
        assertEquals(List.of(OutboundEmailKind.ACCOUNT_EMAIL_VERIFICATION), outbox.kinds());
    }

    @Test
    void refuses_a_password_that_fails_the_policy_without_creating_anything() {
        assertThrows(DomainRuleViolation.class, () -> handler().execute(command("carolina1234")));

        assertTrue(people.byEmail.isEmpty());
        assertTrue(accounts.byEmail.isEmpty());
        assertTrue(outbox.messages.isEmpty());
    }

    @Test
    void stops_before_any_work_when_the_address_is_being_hammered() {
        rateLimit.allowed = false;

        assertThrows(RateLimitExceededException.class, () -> handler().execute(command(PASSWORD)));

        assertTrue(accounts.byEmail.isEmpty());
        assertTrue(outbox.messages.isEmpty());
    }

    @Test
    void counts_every_attempt_because_the_response_never_reveals_the_outcome() {
        handler().execute(command(PASSWORD));

        assertEquals(1, rateLimit.recorded);
    }

    private static RegisterLenderCommand command(String password) {
        return new RegisterLenderCommand(EMAIL, password.toCharArray(), "Carolina", "Restrepo", "127.0.0.1");
    }

    private static UnitOfWorkPort directUnitOfWork() {
        return new UnitOfWorkPort() {
            @Override public <T> T execute(Supplier<T> operation) { return operation.get(); }
        };
    }

    private static final class InMemoryPeople implements PersonRepository {
        private final Map<EmailAddress, Person> byEmail = new HashMap<>();
        @Override public Optional<Person> findById(PersonId id) {
            return byEmail.values().stream().filter(person -> person.id().equals(id)).findFirst();
        }
        @Override public Optional<Person> findByEmail(EmailAddress email) { return Optional.ofNullable(byEmail.get(email)); }
        @Override public Person save(Person aggregate) { byEmail.put(aggregate.primaryEmail(), aggregate); return aggregate; }
        @Override public void delete(PersonId id) { }
        @Override public boolean exists(PersonId id) { return findById(id).isPresent(); }
    }

    private static final class InMemoryAccounts implements UserAccountRepository {
        private final Map<EmailAddress, UserAccount> byEmail = new HashMap<>();
        @Override public Optional<UserAccount> findById(UserAccountId id) {
            return byEmail.values().stream().filter(account -> account.id().equals(id)).findFirst();
        }
        @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.ofNullable(byEmail.get(email)); }
        @Override public UserAccount save(UserAccount aggregate) { byEmail.put(EMAIL, aggregate); return aggregate; }
        @Override public void delete(UserAccountId id) { }
        @Override public boolean exists(UserAccountId id) { return findById(id).isPresent(); }
    }

    private static final class RecordingVerifications implements AccountVerificationPort {
        private final List<UserAccountId> issuedFor = new ArrayList<>();
        @Override public IssuedAccountVerification issue(
                UserAccountId accountId, AccountVerificationPurpose purpose, Instant issuedAt) {
            issuedFor.add(accountId);
            return new IssuedAccountVerification(UUID.randomUUID(), "raw-verification-token");
        }
        @Override public Optional<ClaimedAccountVerification> claim(
                String rawToken, AccountVerificationPurpose purpose, Instant claimedAt) {
            throw new UnsupportedOperationException("Not used by this test");
        }
        @Override public void markPrimaryEmailVerified(UserAccountId accountId, Instant verifiedAt) { }
    }

    private static final class RecordingOutbox implements OutboxEventsPort {
        private final List<OutboundEmailMessage> messages = new ArrayList<>();
        @Override public void enqueue(List<DomainEvent> domainEvents) { }
        @Override public void enqueueEmail(OutboundEmailMessage message, Instant occurredAt) { messages.add(message); }
        private List<OutboundEmailKind> kinds() { return messages.stream().map(OutboundEmailMessage::kind).toList(); }
    }

    private static final class CountingRateLimit implements RegistrationRateLimitPort {
        private boolean allowed = true;
        private int recorded;
        @Override public void checkAllowed(EmailAddress email, String sourceIp, Instant occurredAt) {
            if (!allowed) throw new RateLimitExceededException();
        }
        @Override public void recordAttempt(EmailAddress email, String sourceIp, Instant occurredAt) { recorded++; }
    }
}
