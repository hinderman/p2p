package com.project.backend.application.command;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.AuthenticationFailedException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.PasswordVerifierPort;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.repository.UserAccountRepository;

import java.util.Arrays;
import java.util.Objects;

/** Authentication use case. JWT or session issuance is an outbound-port concern. */
public final class SignInHandler implements CommandHandler<SignInCommand, AuthenticatedSession> {
    private final UserAccountRepository accounts;
    private final PasswordVerifierPort passwordVerifier;
    private final AuthenticationTokenIssuerPort tokenIssuer;
    private final ClockPort clock;

    public SignInHandler(
            UserAccountRepository accounts,
            PasswordVerifierPort passwordVerifier,
            AuthenticationTokenIssuerPort tokenIssuer,
            ClockPort clock) {
        this.accounts = Objects.requireNonNull(accounts, "The user account repository is required");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "The password verifier is required");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "The authentication token issuer is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
    }

    @Override
    public AuthenticatedSession execute(SignInCommand command) {
        char[] password = command.password();
        try {
            UserAccount account = accounts.findByEmail(command.email())
                    .orElseThrow(AuthenticationFailedException::new);
            if (account.status() != UserAccountStatus.ACTIVE
                    || !passwordVerifier.matches(password, account.passwordHash())) {
                throw new AuthenticationFailedException();
            }
            return tokenIssuer.issue(account, clock.now());
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
