package com.project.backend.application.command;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.dto.ClaimedAccountVerification;
import com.project.backend.application.dto.IssuedAccountVerification;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.exception.VerificationInvalidException;
import com.project.backend.application.port.out.AccountVerificationPort;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.domain.event.DomainEvent;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.PersonStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyAccountEmailHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final EmailAddress EMAIL = new EmailAddress("carolina@example.com");

    private final Person person = Person.register(
            new PersonId(UUID.randomUUID()), EMAIL, "Carolina", "Restrepo", NOW.minusSeconds(600));
    private final UserAccount account = UserAccount.createPending(new UserAccountId(UUID.randomUUID()), person.id(),
            new PasswordHash("argon2id:hash"), Set.of(UserRole.LENDER), NOW.minusSeconds(600));

    private final InMemoryPeople people = new InMemoryPeople(person);
    private final InMemoryAccounts accounts = new InMemoryAccounts(account);
    private final RecordingVerifications verifications = new RecordingVerifications(account.id());
    private final RecordingOutbox outbox = new RecordingOutbox();

    private VerifyAccountEmailHandler handler() {
        return new VerifyAccountEmailHandler(verifications, accounts, people, sessionIssuer(), () -> NOW,
                outbox, directUnitOfWork());
    }

    @Test
    void activates_the_account_and_the_person_then_signs_them_in() {
        AuthenticatedSession session = handler().execute(new VerifyAccountEmailCommand("raw-verification-token"));

        assertEquals(UserAccountStatus.ACTIVE, account.status());
        assertEquals(PersonStatus.ACTIVE, person.status());
        assertEquals(account.id(), session.userAccountId());
        assertEquals(Set.of(UserRole.LENDER), session.roles());
    }

    @Test
    void records_that_the_address_is_now_proven_reachable() {
        handler().execute(new VerifyAccountEmailCommand("raw-verification-token"));

        assertEquals(List.of(account.id()), verifications.markedVerified);
    }

    /** Activation is a domain event that downstream consumers must see exactly once. */
    @Test
    void publishes_the_activation_and_leaves_no_event_pending_on_the_aggregate() {
        handler().execute(new VerifyAccountEmailCommand("raw-verification-token"));

        assertEquals(1, outbox.events.size());
        assertTrue(account.domainEvents().isEmpty());
    }

    @Test
    void rejects_a_token_that_cannot_be_claimed() {
        verifications.claimable = false;

        assertThrows(VerificationInvalidException.class,
                () -> handler().execute(new VerifyAccountEmailCommand("raw-verification-token")));
        assertEquals(UserAccountStatus.PENDING_VERIFICATION, account.status());
    }

    /** A token is single use: the second attempt must fail even with the same value. */
    @Test
    void rejects_a_second_use_of_the_same_token() {
        handler().execute(new VerifyAccountEmailCommand("raw-verification-token"));

        assertThrows(VerificationInvalidException.class,
                () -> handler().execute(new VerifyAccountEmailCommand("raw-verification-token")));
    }

    private static AuthenticationTokenIssuerPort sessionIssuer() {
        return (account, issuedAt) -> new AuthenticatedSession(account.id(), account.personId(), account.roles(),
                "access", "refresh", issuedAt.plusSeconds(900));
    }

    private static UnitOfWorkPort directUnitOfWork() {
        return new UnitOfWorkPort() {
            @Override public <T> T execute(Supplier<T> operation) { return operation.get(); }
        };
    }

    private static final class InMemoryPeople implements PersonRepository {
        private Person person;
        private InMemoryPeople(Person person) { this.person = person; }
        @Override public Optional<Person> findById(PersonId id) { return person.id().equals(id) ? Optional.of(person) : Optional.empty(); }
        @Override public Optional<Person> findByEmail(EmailAddress email) { return person.primaryEmail().equals(email) ? Optional.of(person) : Optional.empty(); }
        @Override public Person save(Person aggregate) { person = aggregate; return aggregate; }
        @Override public void delete(PersonId id) { }
        @Override public boolean exists(PersonId id) { return person.id().equals(id); }
    }

    private static final class InMemoryAccounts implements UserAccountRepository {
        private UserAccount account;
        private InMemoryAccounts(UserAccount account) { this.account = account; }
        @Override public Optional<UserAccount> findById(UserAccountId id) { return account.id().equals(id) ? Optional.of(account) : Optional.empty(); }
        @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.of(account); }
        @Override public UserAccount save(UserAccount aggregate) { account = aggregate; return aggregate; }
        @Override public void delete(UserAccountId id) { }
        @Override public boolean exists(UserAccountId id) { return account.id().equals(id); }
    }

    private static final class RecordingVerifications implements AccountVerificationPort {
        private final UserAccountId accountId;
        private final List<UserAccountId> markedVerified = new ArrayList<>();
        private boolean claimable = true;
        private RecordingVerifications(UserAccountId accountId) { this.accountId = accountId; }
        @Override public IssuedAccountVerification issue(
                UserAccountId accountId, AccountVerificationPurpose purpose, Instant issuedAt) {
            throw new UnsupportedOperationException("Not used by this test");
        }
        @Override public Optional<ClaimedAccountVerification> claim(
                String rawToken, AccountVerificationPurpose purpose, Instant claimedAt) {
            if (!claimable) return Optional.empty();
            claimable = false;
            return Optional.of(new ClaimedAccountVerification(UUID.randomUUID(), accountId, purpose));
        }
        @Override public void markPrimaryEmailVerified(UserAccountId accountId, Instant verifiedAt) {
            markedVerified.add(accountId);
        }
    }

    private static final class RecordingOutbox implements OutboxEventsPort {
        private final List<DomainEvent> events = new ArrayList<>();
        @Override public void enqueue(List<DomainEvent> domainEvents) { events.addAll(domainEvents); }
        @Override public void enqueueEmail(OutboundEmailMessage message, Instant occurredAt) { }
    }
}
