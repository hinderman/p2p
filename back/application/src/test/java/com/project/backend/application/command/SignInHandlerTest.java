package com.project.backend.application.command;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.AuthenticationFailedException;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.PasswordVerifierPort;
import com.project.backend.application.port.out.SignInRateLimitPort;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignInHandlerTest {

    @Test
    void issues_a_session_for_valid_credentials() {
        UserAccount account = activeAccount();
        SignInHandler handler = new SignInHandler(
                new FakeUserAccountRepository(account),
                (password, hash) -> String.valueOf(password).equals("secret") && hash.value().equals("hash"),
                emisor(),
                () -> Instant.parse("2026-08-18T12:00:00Z"),
                unlimitedRateLimiter());

        AuthenticatedSession session = handler.execute(new SignInCommand(
                new EmailAddress("payer@example.com"), "secret".toCharArray(), "127.0.0.1"));

        assertEquals(account.id(), session.userAccountId());
        assertEquals("access", session.accessToken());
    }

    @Test
    void does_not_reveal_when_the_password_is_invalid() {
        UserAccount account = activeAccount();
        SignInHandler handler = new SignInHandler(
                new FakeUserAccountRepository(account),
                (password, hash) -> false,
                emisor(),
                Instant::now,
                unlimitedRateLimiter());

        assertThrows(AuthenticationFailedException.class, () -> handler.execute(new SignInCommand(
                new EmailAddress("payer@example.com"), "invalid".toCharArray(), "127.0.0.1")));
    }

    private static UserAccount activeAccount() {
        return UserAccount.rehydrate(
                new UserAccountId(UUID.randomUUID()), new PersonId(UUID.randomUUID()), new PasswordHash("hash"),
                UserAccountStatus.ACTIVE, Set.of(UserRole.PAYER), 0,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static AuthenticationTokenIssuerPort emisor() {
        return (account, issuedAt) -> new AuthenticatedSession(
                account.id(), account.personId(), account.roles(), "access", "refresh", issuedAt.plusSeconds(900));
    }

    private static SignInRateLimitPort unlimitedRateLimiter() {
        return new SignInRateLimitPort() {
            @Override public void checkAllowed(EmailAddress email, String sourceIp, Instant occurredAt) { }
            @Override public void recordAttempt(EmailAddress email, String sourceIp, boolean succeeded, Instant occurredAt) { }
        };
    }

    private static final class FakeUserAccountRepository implements UserAccountRepository {
        private final UserAccount account;

        private FakeUserAccountRepository(UserAccount account) { this.account = account; }

        @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.of(account); }
        @Override public Optional<UserAccount> findById(UserAccountId id) { return account.id().equals(id) ? Optional.of(account) : Optional.empty(); }
        @Override public UserAccount save(UserAccount aggregate) { return aggregate; }
        @Override public void delete(UserAccountId id) { }
        @Override public boolean exists(UserAccountId id) { return account.id().equals(id); }
    }
}
