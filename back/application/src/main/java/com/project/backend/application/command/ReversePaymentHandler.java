package com.project.backend.application.command;

import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.port.out.FinancialLedgerPort;
import com.project.backend.application.port.out.PaymentAllocationValidationPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.financial.FinancialJournal;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.repository.LoanRepository;

import java.util.Objects;

public final class ReversePaymentHandler implements CommandHandler<ReversePaymentCommand, PaymentProcessed> {
    private final ApplicationAuthorizer authorizer;
    private final ReportedPaymentRepository payments;
    private final LoanRepository loans;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;
    private final FinancialLedgerPort financialLedger;
    private final PaymentAllocationValidationPort allocationValidation;

    public ReversePaymentHandler(
            ApplicationAuthorizer authorizer,
            ReportedPaymentRepository payments,
            LoanRepository loans,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork,
            FinancialLedgerPort financialLedger,
            PaymentAllocationValidationPort allocationValidation) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.payments = Objects.requireNonNull(payments, "El repositorio de payments es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.clock = Objects.requireNonNull(clock, "El clock es obligatorio");
        this.outbox = Objects.requireNonNull(outbox, "El outbox es obligatorio");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "La unidad de trabajo es obligatoria");
        this.financialLedger = Objects.requireNonNull(financialLedger, "The financial ledger is required");
        this.allocationValidation = Objects.requireNonNull(allocationValidation, "The allocation validation port is required");
    }

    @Override
    public Class<ReversePaymentCommand> requestType() {
        return ReversePaymentCommand.class;
    }

    @Override
    public PaymentProcessed execute(ReversePaymentCommand command) {
        return unitOfWork.execute(() -> {
            var lender = authorizer.requireActiveAccountWithRole(command.lenderAccountId(), UserRole.LENDER);
            var paymentSnapshot = payments.findById(command.reportedPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("El payment no exists"));
            allocationValidation.lockLoanForFinancialChange(paymentSnapshot.loanId());
            var payment = payments.findByIdForUpdate(command.reportedPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("El payment no exists"));
            var loan = loans.findById(payment.loanId())
                    .orElseThrow(() -> new ResourceNotFoundException("The payment loan does not exist"));
            authorizer.requireLenderOwnership(lender, loan);
            if (payment.type() == PaymentType.CAPITAL_PREPAYMENT && loan.terms().stream()
                    .filter(term -> term.status() == LoanTermStatus.ACCEPTED)
                    .anyMatch(term -> term.capitalPrepaymentPolicy() != CapitalPrepaymentPolicy.NO_RECALCULATION)) {
                throw new com.project.backend.application.exception.OperationNotAllowedException(
                        "A capital prepayment that recalculated the payment plan requires a corrective plan, not a direct reversal");
            }
            var now = clock.now();
            payment.reverse(lender.id(), command.reason(), now);
            payments.save(payment);
            financialLedger.record(FinancialJournal.reversalFor(payment), now);
            outbox.enqueue(payment.domainEvents());
            payment.pullEvents();
            return new PaymentProcessed(payment.id(), payment.status());
        });
    }
}
