package com.project.backend.domain.identity;

import com.project.backend.domain.entity.AggregateRoot;
import com.project.backend.domain.event.UserAccountActivated;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.UserAccountId;
import com.project.backend.domain.valueobject.PersonId;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Business identity aggregate; it contains neither tokens nor cryptographic algorithms. */
public final class UserAccount extends AggregateRoot<UserAccountId> {
    private final PersonId personId;
    private PasswordHash passwordHash;
    private UserAccountStatus status;
    private final Set<UserRole> roles;
    private long authorizationVersion;
    private Instant passwordChangedAt;
    private final Instant createdAt;

    private UserAccount(
            UserAccountId id,
            PersonId personId,
            PasswordHash passwordHash,
            UserAccountStatus status,
            Set<UserRole> roles,
            long authorizationVersion,
            Instant passwordChangedAt,
            Instant createdAt) {
        super(id);
        this.personId = Objects.requireNonNull(personId, "The account person is required");
        this.passwordHash = Objects.requireNonNull(passwordHash, "The password hash is required");
        this.status = Objects.requireNonNull(status, "The account status is required");
        if (roles == null || roles.isEmpty()) {
            throw new DomainRuleViolation("An account must have at least one role");
        }
        this.roles = EnumSet.copyOf(roles);
        if (authorizationVersion < 0) {
            throw new DomainRuleViolation("The authorization version cannot be negative");
        }
        this.authorizationVersion = authorizationVersion;
        this.passwordChangedAt = Objects.requireNonNull(passwordChangedAt, "The password-change time is required");
        this.createdAt = Objects.requireNonNull(createdAt, "The creation time is required");
    }

    public static UserAccount createPending(
            UserAccountId id,
            PersonId personId,
            PasswordHash passwordHash,
            Set<UserRole> roles,
            Instant createdAt) {
        return new UserAccount(
                id, personId, passwordHash, UserAccountStatus.PENDING_VERIFICATION,
                roles, 0, createdAt, createdAt);
    }

    /** Rehydration constructor for the persistence adapter. */
    public static UserAccount rehydrate(
            UserAccountId id,
            PersonId personId,
            PasswordHash passwordHash,
            UserAccountStatus status,
            Set<UserRole> roles,
            long authorizationVersion,
            Instant passwordChangedAt,
            Instant createdAt) {
        return new UserAccount(
                id, personId, passwordHash, status, roles, authorizationVersion, passwordChangedAt, createdAt);
    }

    public PersonId personId() { return personId; }
    public PasswordHash passwordHash() { return passwordHash; }
    public UserAccountStatus status() { return status; }
    public Set<UserRole> roles() { return Set.copyOf(roles); }
    public boolean hasRole(UserRole role) { return roles.contains(Objects.requireNonNull(role, "The role is required")); }
    public long authorizationVersion() { return authorizationVersion; }
    public Instant passwordChangedAt() { return passwordChangedAt; }
    public Instant createdAt() { return createdAt; }

    public void activate(Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "The activation time is required");
        if (status != UserAccountStatus.PENDING_VERIFICATION) {
            throw new DomainRuleViolation("Only an account pending verification can be activated");
        }
        status = UserAccountStatus.ACTIVE;
        registerEvent(new UserAccountActivated(id(), occurredAt));
    }

    public void assignRole(UserRole role) {
        Objects.requireNonNull(role, "The role is required");
        if (roles.add(role)) {
            authorizationVersion++;
        }
    }

    public void removeRole(UserRole role) {
        Objects.requireNonNull(role, "The role is required");
        if (roles.size() == 1 && roles.contains(role)) {
            throw new DomainRuleViolation("An account must have at least one role");
        }
        if (roles.remove(role)) {
            authorizationVersion++;
        }
    }

    public void changePassword(PasswordHash newHash, Instant occurredAt) {
        Objects.requireNonNull(newHash, "The new password hash is required");
        Objects.requireNonNull(occurredAt, "The password-change time is required");
        if (status == UserAccountStatus.INACTIVE) {
            throw new DomainRuleViolation("The password of an inactive account cannot be changed");
        }
        passwordHash = newHash;
        passwordChangedAt = occurredAt;
        authorizationVersion++;
    }

    public void block() {
        if (status != UserAccountStatus.ACTIVE) {
            throw new DomainRuleViolation("Only an active account can be blocked");
        }
        status = UserAccountStatus.BLOCKED;
        authorizationVersion++;
    }
}
