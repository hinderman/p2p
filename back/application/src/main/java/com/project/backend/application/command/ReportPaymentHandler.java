package com.project.backend.application.command;

import com.project.backend.application.dto.PaymentRegistered;
import com.project.backend.application.dto.StoredObjectScanStatus;
import com.project.backend.application.exception.InvalidPaymentProofException;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.exception.OperationNotAllowedException;
import com.project.backend.application.exception.IdempotencyConflictException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.port.out.StoredObjectPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.valueobject.ReportedPaymentId;

import java.time.Instant;
import java.util.Objects;

/** Records an external payment and submits it for lender review. */
public final class ReportPaymentHandler implements CommandHandler<ReportPaymentCommand, PaymentRegistered> {
    private final ApplicationAuthorizer authorizer;
    private final LoanRepository loans;
    private final ReportedPaymentRepository payments;
    private final StoredObjectPort storedObjects;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public ReportPaymentHandler(
            ApplicationAuthorizer authorizer,
            LoanRepository loans,
            ReportedPaymentRepository payments,
            StoredObjectPort storedObjects,
            UuidGeneratorPort uuids,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.payments = Objects.requireNonNull(payments, "El repositorio de payments es obligatorio");
        this.storedObjects = Objects.requireNonNull(storedObjects, "The stored object port is required");
        this.uuids = Objects.requireNonNull(uuids, "El generador de UUID es obligatorio");
        this.clock = Objects.requireNonNull(clock, "El clock es obligatorio");
        this.outbox = Objects.requireNonNull(outbox, "El outbox es obligatorio");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "La unidad de trabajo es obligatoria");
    }

    @Override
    public Class<ReportPaymentCommand> requestType() {
        return ReportPaymentCommand.class;
    }

    @Override
    public PaymentRegistered execute(ReportPaymentCommand command) {
        return unitOfWork.execute(() -> {
            var payer = authorizer.requireActiveAccountWithRole(command.payerAccountId(), UserRole.PAYER);
            var priorSubmission = payments.findByIdempotencyKey(command.idempotencyKey());
            if (priorSubmission.isPresent()) {
                return replayOrRejectChangedSubmission(priorSubmission.get(), command);
            }
            var loan = loans.findById(command.loanId())
                    .orElseThrow(() -> new ResourceNotFoundException("The loan does not exist"));
            authorizer.requirePayerOwnership(payer, loan);
            if (loan.status() != LoanStatus.ACTIVE) {
                throw new OperationNotAllowedException("Payments can only be reported for active loans");
            }
            if (command.paymentType() == PaymentType.PAYOFF) {
                throw new OperationNotAllowedException(
                        "PAYOFF is unavailable until the settlement workflow can close the loan and its payment plan atomically");
            }
            validateProofs(command);
            Instant now = clock.now();
            ReportedPayment payment = ReportedPayment.create(
                    new ReportedPaymentId(uuids.nextUuid()), loan.id(), payer.personId(), payer.id(),
                    command.paymentType(), command.reportedAmount(), command.reportedPaymentDate(),
                    command.externalReference(), command.idempotencyKey(), now);
            command.proofs().forEach(payment::attachProof);
            payment.submitForReview(now);
            ReportedPayment persisted = payments.save(payment);
            if (!persisted.id().equals(payment.id())) {
                return replayOrRejectChangedSubmission(persisted, command);
            }
            outbox.enqueue(payment.domainEvents());
            payment.pullEvents();
            return new PaymentRegistered(payment.id(), payment.status());
        });
    }

    private void validateProofs(ReportPaymentCommand command) {
        for (var proof : command.proofs()) {
            var stored = storedObjects.findByIdForUpdate(proof.storedObjectId())
                    .orElseThrow(() -> new InvalidPaymentProofException("The payment proof does not exist"));
            if (!stored.uploadedBy().equals(command.payerAccountId())) {
                throw new InvalidPaymentProofException("The payment proof belongs to another account");
            }
            if (stored.scanStatus() != StoredObjectScanStatus.SAFE) {
                throw new InvalidPaymentProofException("The payment proof has not passed malware scanning");
            }
            if (stored.attached()) {
                throw new InvalidPaymentProofException("The payment proof is already attached to a payment");
            }
            if (!stored.sha256().equals(proof.sha256())) {
                throw new InvalidPaymentProofException("The payment proof digest does not match the stored object");
            }
        }
    }

    private PaymentRegistered replayOrRejectChangedSubmission(ReportedPayment payment, ReportPaymentCommand command) {
        if (!payment.matchesSubmission(command.loanId(), command.payerAccountId(), command.paymentType(),
                command.reportedAmount(), command.reportedPaymentDate(), command.externalReference(), command.proofs())) {
            throw new IdempotencyConflictException("The Idempotency-Key was already used with a different payment submission");
        }
        return new PaymentRegistered(payment.id(), payment.status());
    }
}
