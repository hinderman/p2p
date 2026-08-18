package com.project.backend.application.command;

import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.event.DomainEvent;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.payment.ReportedPayment;
import com.project.backend.domain.payment.PaymentType;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.service.PaymentPlanGenerator;
import com.project.backend.domain.valueobject.PaymentPlanId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ApprovePaymentHandler implements CommandHandler<ApprovePaymentCommand, PaymentProcessed> {
    private final ApplicationAuthorizer authorizer;
    private final LoanRepository loans;
    private final ReportedPaymentRepository payments;
    private final PaymentPlanGenerator paymentPlanGenerator;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;
    private final OutboxEventsPort outbox;
    private final UnitOfWorkPort unitOfWork;

    public ApprovePaymentHandler(
            ApplicationAuthorizer authorizer,
            LoanRepository loans,
            ReportedPaymentRepository payments,
            PaymentPlanGenerator paymentPlanGenerator,
            UuidGeneratorPort uuids,
            ClockPort clock,
            OutboxEventsPort outbox,
            UnitOfWorkPort unitOfWork) {
        this.authorizer = Objects.requireNonNull(authorizer, "El authorizer es obligatorio");
        this.loans = Objects.requireNonNull(loans, "The loan repository is required");
        this.payments = Objects.requireNonNull(payments, "El repositorio de payments es obligatorio");
        this.paymentPlanGenerator = Objects.requireNonNull(paymentPlanGenerator, "El generador de paymentPlan es obligatorio");
        this.uuids = Objects.requireNonNull(uuids, "El generador de UUID es obligatorio");
        this.clock = Objects.requireNonNull(clock, "El clock es obligatorio");
        this.outbox = Objects.requireNonNull(outbox, "El outbox es obligatorio");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "La unidad de trabajo es obligatoria");
    }

    @Override
    public PaymentProcessed execute(ApprovePaymentCommand command) {
        return unitOfWork.execute(() -> {
            var lender = authorizer.requireActiveAccountWithRole(command.lenderAccountId(), UserRole.LENDER);
            ReportedPayment payment = payments.findById(command.reportedPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("El payment no exists"));
            Loan loan = loans.findById(payment.loanId())
                    .orElseThrow(() -> new ResourceNotFoundException("The payment loan does not exist"));
            authorizer.requireLenderOwnership(lender, loan);
            Instant now = clock.now();
            payment.approve(lender.id(), command.validatedAmount(), command.allocations(), now);

            if (shouldRecalculatePlan(loan, payment)) {
                var newPaymentPlan = paymentPlanGenerator.recalculateAfterCapitalPrepayment(new PaymentPlanId(uuids.nextUuid()), loan, payment, now);
                loan.replacePlanAfterCapitalPrepayment(newPaymentPlan, now);
                loans.save(loan);
            }
            payments.save(payment);
            List<DomainEvent> domainEvents = new ArrayList<>(payment.domainEvents());
            domainEvents.addAll(loan.domainEvents());
            outbox.enqueue(domainEvents);
            payment.pullEvents();
            loan.pullEvents();
            return new PaymentProcessed(payment.id(), payment.status());
        });
    }

    private boolean shouldRecalculatePlan(Loan loan, ReportedPayment payment) {
        if (payment.type() != PaymentType.CAPITAL_PREPAYMENT) return false;
        return loan.terms().stream()
                .filter(term -> term.status() == LoanTermStatus.ACCEPTED)
                .findFirst()
                .map(term -> term.capitalPrepaymentPolicy() != CapitalPrepaymentPolicy.NO_RECALCULATION)
                .orElse(false);
    }
}
