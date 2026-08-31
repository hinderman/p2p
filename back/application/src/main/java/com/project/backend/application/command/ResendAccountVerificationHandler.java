package com.project.backend.application.command;

import com.project.backend.application.dto.AccountVerificationPurpose;
import com.project.backend.application.dto.IssuedAccountVerification;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.AccountVerificationPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.RegistrationRateLimitPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.repository.UserAccountRepository;

import java.time.Instant;
import java.util.Objects;

/**
 * Issues a replacement verification link, superseding any link still pending.
 *
 * <p>Like registration, the result is the same whether or not the address has an
 * account awaiting verification, so the endpoint reveals nothing.
 */
public final class ResendAccountVerificationHandler
        implements CommandHandler<ResendAccountVerificationCommand, Void> {
    private final UserAccountRepository accounts;
    private final AccountVerificationPort verifications;
    private final RegistrationRateLimitPort rateLimit;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public ResendAccountVerificationHandler(
            UserAccountRepository accounts,
            AccountVerificationPort verifications,
            RegistrationRateLimitPort rateLimit,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.accounts = Objects.requireNonNull(accounts, "The user account repository is required");
        this.verifications = Objects.requireNonNull(verifications, "The account verification port is required");
        this.rateLimit = Objects.requireNonNull(rateLimit, "The registration rate-limit port is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
        this.outbox = Objects.requireNonNull(outbox, "The outbox port is required");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "The unit of work is required");
    }

    @Override
    public Class<ResendAccountVerificationCommand> requestType() {
        return ResendAccountVerificationCommand.class;
    }

    @Override
    public Void execute(ResendAccountVerificationCommand command) {
        return unitOfWork.execute(() -> resend(command));
    }

    private Void resend(ResendAccountVerificationCommand command) {
        Instant now = clock.now();
        rateLimit.checkAllowed(command.email(), command.sourceIp(), now);
        rateLimit.recordAttempt(command.email(), command.sourceIp(), now);

        accounts.findByEmail(command.email())
                .filter(account -> account.status() == UserAccountStatus.PENDING_VERIFICATION)
                .ifPresent(account -> {
                    IssuedAccountVerification issued = verifications.issue(
                            account.id(), AccountVerificationPurpose.EMAIL_VERIFICATION, now);
                    outbox.enqueueEmail(OutboundEmailMessage.accountEmailVerification(
                            issued.verificationId(), command.email(), issued.rawToken()), now);
                });
        return null;
    }
}
