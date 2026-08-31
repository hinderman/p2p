package com.project.backend.application.security;

import com.project.backend.application.exception.AccessDeniedException;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.UserAccountId;

import java.util.Objects;

/** Single point for active-account and role checks required by use cases. */
public final class ApplicationAuthorizer {
    private final UserAccountRepository accounts;

    public ApplicationAuthorizer(UserAccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "El repositorio de accounts es obligatorio");
    }

    public UserAccount requireActiveAccountWithRole(UserAccountId accountId, UserRole role) {
        UserAccount account = requireActiveAccount(accountId);
        if (!account.hasRole(role)) {
            throw new AccessDeniedException("The account is not authorized to perform this operation");
        }
        return account;
    }

    public UserAccount requireActiveAccount(UserAccountId accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("La account no exists"));
        if (account.status() != UserAccountStatus.ACTIVE) {
            throw new AccessDeniedException("The account is not authorized to perform this operation");
        }
        return account;
    }

    public void requireLenderOwnership(UserAccount account, Loan loan) {
        if (!loan.lenderPersonId().equals(account.personId())) {
            throw new AccessDeniedException("The account is not the loan lender");
        }
    }

    public void requirePayerOwnership(UserAccount account, Loan loan) {
        if (!loan.payerPersonId().equals(account.personId())) {
            throw new AccessDeniedException("The account is not the loan payer");
        }
    }
}
