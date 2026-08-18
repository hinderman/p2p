package com.project.backend.domain.identity;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountTest {

    @Test
    void activates_the_account_and_versions_role_changes() {
        UserAccount account = UserAccount.createPending(
                new UserAccountId(UUID.randomUUID()), new PersonId(UUID.randomUUID()),
                new PasswordHash("$argon2id$hash"), Set.of(UserRole.PAYER), Instant.now());

        account.activate(Instant.now());
        account.assignRole(UserRole.LENDER);

        assertEquals(UserAccountStatus.ACTIVE, account.status());
        assertTrue(account.hasRole(UserRole.LENDER));
        assertEquals(1, account.authorizationVersion());
        assertEquals(1, account.pullEvents().size());
    }

    @Test
    void impide_retirar_el_ultimo_rol() {
        UserAccount account = UserAccount.createPending(
                new UserAccountId(UUID.randomUUID()), new PersonId(UUID.randomUUID()),
                new PasswordHash("$argon2id$hash"), Set.of(UserRole.PAYER), Instant.now());

        assertThrows(DomainRuleViolation.class, () -> account.removeRole(UserRole.PAYER));
        assertFalse(account.roles().isEmpty());
    }
}
