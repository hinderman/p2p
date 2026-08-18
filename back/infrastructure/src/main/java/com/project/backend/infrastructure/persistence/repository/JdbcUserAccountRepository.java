package com.project.backend.infrastructure.persistence.repository;

import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public final class JdbcUserAccountRepository implements UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findById(UserAccountId id) {
        return findSingle("SELECT * FROM loans.user_accounts WHERE user_account_id = ?", id.value());
    }

    @Override
    public Optional<UserAccount> findByEmail(EmailAddress email) {
        return findSingle("""
                SELECT account.* FROM loans.user_accounts account
                JOIN loans.person_emails email ON email.person_id = account.person_id AND email.is_primary
                WHERE email.normalized_email = ?
                """, email.value());
    }

    @Override
    public UserAccount save(UserAccount account) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE loans.user_accounts
                SET password_hash = ?, status = ?, password_changed_at = ?, authorization_version = ?,
                    updated_at = ?, version = version + 1
                WHERE user_account_id = ?
                """, account.passwordHash().value(), account.status().name(), account.passwordChangedAt(),
                account.authorizationVersion(), now, account.id().value());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO loans.user_accounts
                    (user_account_id, person_id, password_hash, status, password_changed_at,
                     authorization_version, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, account.id().value(), account.personId().value(), account.passwordHash().value(),
                    account.status().name(), account.passwordChangedAt(), account.authorizationVersion(),
                    account.createdAt(), now);
        }
        synchronizeRoles(account, now);
        return account;
    }

    @Override
    public void delete(UserAccountId id) {
        jdbcTemplate.update("DELETE FROM loans.user_account_roles WHERE user_account_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.user_accounts WHERE user_account_id = ?", id.value());
    }

    @Override
    public boolean exists(UserAccountId id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM loans.user_accounts WHERE user_account_id = ?)", Boolean.class, id.value()));
    }

    private Optional<UserAccount> findSingle(String sql, Object argument) {
        List<UserAccount> accounts = jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            UUID accountId = resultSet.getObject("user_account_id", UUID.class);
            return UserAccount.rehydrate(
                    new UserAccountId(accountId),
                    new PersonId(resultSet.getObject("person_id", UUID.class)),
                    new PasswordHash(resultSet.getString("password_hash")),
                    UserAccountStatus.valueOf(resultSet.getString("status")),
                    findRoles(accountId),
                    resultSet.getLong("authorization_version"),
                    resultSet.getObject("password_changed_at", Instant.class),
                    resultSet.getObject("created_at", Instant.class));
        }, argument);
        return accounts.stream().findFirst();
    }

    private Set<UserRole> findRoles(UUID accountId) {
        List<UserRole> roles = jdbcTemplate.queryForList("""
                SELECT role_code FROM loans.user_account_roles WHERE user_account_id = ?
                """, String.class, accountId).stream().map(UserRole::valueOf).toList();
        return roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles);
    }

    private void synchronizeRoles(UserAccount account, Instant grantedAt) {
        jdbcTemplate.update("DELETE FROM loans.user_account_roles WHERE user_account_id = ? AND role_code NOT IN (?, ?)",
                account.id().value(), UserRole.LENDER.name(), UserRole.PAYER.name());
        for (UserRole role : account.roles()) {
            jdbcTemplate.update("""
                    INSERT INTO loans.user_account_roles (user_account_id, role_code, granted_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (user_account_id, role_code) DO NOTHING
                    """, account.id().value(), role.name(), grantedAt);
        }
    }
}
