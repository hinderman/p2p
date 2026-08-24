package com.project.backend.infrastructure.security;

import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.application.security.AuthenticatedAccessToken;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtAccessTokenCodecTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void verifies_an_hs512_token_with_complete_claims() {
        UserAccount account = account();
        JwtAccessTokenCodec codec = new JwtAccessTokenCodec(properties());
        UUID sessionId = UUID.randomUUID();

        String token = codec.issue(account, sessionId, ISSUED_AT, ISSUED_AT.plusSeconds(900));
        AuthenticatedAccessToken claims = codec.decodeAndVerify(token, ISSUED_AT.plusSeconds(1));

        assertEquals(account.id(), claims.accountId());
        assertEquals(account.personId(), claims.personId());
        assertEquals(sessionId, claims.sessionId());
        assertEquals(Set.of(UserRole.LENDER, UserRole.PAYER), claims.roles());
    }

    @Test
    void rejects_a_tampered_or_expired_token() {
        JwtAccessTokenCodec codec = new JwtAccessTokenCodec(properties());
        String token = codec.issue(account(), UUID.randomUUID(), ISSUED_AT, ISSUED_AT.plusSeconds(60));

        assertThrows(IllegalArgumentException.class, () -> codec.decodeAndVerify(token + "x", ISSUED_AT.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeAndVerify(token, ISSUED_AT.plusSeconds(60)));
    }

    private static JwtAuthenticationProperties properties() {
        return new JwtAuthenticationProperties(Base64.getEncoder().encodeToString(new byte[64]), "project-backend",
                Duration.ofMinutes(15), Duration.ofDays(30));
    }

    private static UserAccount account() {
        return UserAccount.rehydrate(new UserAccountId(UUID.randomUUID()), new PersonId(UUID.randomUUID()),
                new PasswordHash("hash"), UserAccountStatus.ACTIVE, Set.of(UserRole.LENDER, UserRole.PAYER), 3,
                ISSUED_AT, ISSUED_AT);
    }
}
