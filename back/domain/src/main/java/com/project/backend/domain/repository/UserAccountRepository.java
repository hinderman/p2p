package com.project.backend.domain.repository;

import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Optional;

/** Identity port for sign-in and account management. */
public interface UserAccountRepository extends Repository<UserAccount, UserAccountId> {

    Optional<UserAccount> findByEmail(EmailAddress email);
}
