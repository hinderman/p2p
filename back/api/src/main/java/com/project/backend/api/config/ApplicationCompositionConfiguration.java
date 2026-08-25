package com.project.backend.api.config;

import com.project.backend.api.observability.ApplicationRequestMetricsPipeline;
import com.project.backend.application.command.AcceptLoanCommand;
import com.project.backend.application.command.AcceptLoanHandler;
import com.project.backend.application.command.ApprovePaymentCommand;
import com.project.backend.application.command.ApprovePaymentHandler;
import com.project.backend.application.command.CreateLoanCommand;
import com.project.backend.application.command.CreateLoanHandler;
import com.project.backend.application.command.CompletePayerOnboardingCommand;
import com.project.backend.application.command.CompletePayerOnboardingHandler;
import com.project.backend.application.command.RejectPaymentCommand;
import com.project.backend.application.command.RejectPaymentHandler;
import com.project.backend.application.command.RefreshSessionCommand;
import com.project.backend.application.command.RefreshSessionHandler;
import com.project.backend.application.command.ReportPaymentCommand;
import com.project.backend.application.command.ReportPaymentHandler;
import com.project.backend.application.command.RevokeSessionsCommand;
import com.project.backend.application.command.RevokeSessionsHandler;
import com.project.backend.application.command.ReversePaymentCommand;
import com.project.backend.application.command.ReversePaymentHandler;
import com.project.backend.application.command.SignInCommand;
import com.project.backend.application.command.SignInHandler;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.dto.LoanAccepted;
import com.project.backend.application.dto.LoanCreated;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PaymentProcessed;
import com.project.backend.application.dto.PaymentRegistered;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.mediator.PipelineApplicationMediator;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.application.port.in.RequestHandler;
import com.project.backend.application.port.in.RequestPipeline;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.AuthenticationSessionPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.FinancialLedgerPort;
import com.project.backend.application.port.out.LoanInvitationPort;
import com.project.backend.application.port.out.LoanReadModelPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.PasswordVerifierPort;
import com.project.backend.application.port.out.PasswordHashingPort;
import com.project.backend.application.port.out.PaymentReadModelPort;
import com.project.backend.application.port.out.PaymentAllocationValidationPort;
import com.project.backend.application.port.out.SignInRateLimitPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.application.query.ListLenderLoansHandler;
import com.project.backend.application.query.ListLenderLoansQuery;
import com.project.backend.application.query.ListPayerLoansHandler;
import com.project.backend.application.query.ListPayerLoansQuery;
import com.project.backend.application.query.ListPendingPaymentsHandler;
import com.project.backend.application.query.ListPendingPaymentsQuery;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.service.PaymentPlanGenerator;
import com.project.backend.domain.service.StandardPaymentPlanGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Explicit composition root; framework annotations never leak into application or domain. */
@Configuration
public class ApplicationCompositionConfiguration {

    /**
     * The API remains the composition root: application handlers are explicit
     * beans, while this mediator discovers those typed input ports and applies
     * only payload-safe cross-cutting behavior.
     */
    @Bean
    ApplicationMediator applicationMediator(
            List<RequestHandler<?, ?>> handlers,
            ObjectProvider<MeterRegistry> meterRegistries) {
        List<RequestPipeline> pipelines = meterRegistries.orderedStream()
                .<RequestPipeline>map(ApplicationRequestMetricsPipeline::new)
                .toList();
        return new PipelineApplicationMediator(handlers, pipelines);
    }

    @Bean
    ApplicationAuthorizer applicationAuthorizer(UserAccountRepository accounts) {
        return new ApplicationAuthorizer(accounts);
    }

    @Bean
    PaymentPlanGenerator paymentPlanGenerator() {
        return new StandardPaymentPlanGenerator();
    }

    @Bean
    CommandHandler<SignInCommand, AuthenticatedSession> signInHandler(
            UserAccountRepository accounts, PasswordVerifierPort passwords,
            AuthenticationTokenIssuerPort tokens, ClockPort clock, SignInRateLimitPort rateLimit) {
        return new SignInHandler(accounts, passwords, tokens, clock, rateLimit);
    }

    @Bean
    CommandHandler<CompletePayerOnboardingCommand, AuthenticatedSession> completePayerOnboardingHandler(
            LoanInvitationPort invitations, PersonRepository people, UserAccountRepository accounts, LoanRepository loans,
            PasswordHashingPort passwordHasher, PasswordVerifierPort passwordVerifier, PaymentPlanGenerator paymentPlans,
            AuthenticationTokenIssuerPort tokenIssuer, UuidGeneratorPort uuids, ClockPort clock,
            OutboxEventsPort outbox, UnitOfWorkPort unitOfWork) {
        return new CompletePayerOnboardingHandler(invitations, people, accounts, loans, passwordHasher, passwordVerifier,
                paymentPlans, tokenIssuer, uuids, clock, outbox, unitOfWork);
    }

    @Bean
    CommandHandler<RefreshSessionCommand, AuthenticatedSession> refreshSessionHandler(
            AuthenticationSessionPort sessions, ClockPort clock) {
        return new RefreshSessionHandler(sessions, clock);
    }

    @Bean
    CommandHandler<RevokeSessionsCommand, Void> revokeSessionsHandler(
            AuthenticationSessionPort sessions, ClockPort clock) {
        return new RevokeSessionsHandler(sessions, clock);
    }

    @Bean
    CommandHandler<CreateLoanCommand, LoanCreated> createLoanHandler(
            ApplicationAuthorizer authorizer, PersonRepository people, LoanRepository loans,
            UuidGeneratorPort uuids, ClockPort clock, LoanInvitationPort invitations,
            OutboxEventsPort outbox, UnitOfWorkPort unitOfWork) {
        return new CreateLoanHandler(authorizer, people, loans, uuids, clock, invitations, outbox, unitOfWork);
    }

    @Bean
    CommandHandler<AcceptLoanCommand, LoanAccepted> acceptLoanHandler(
            ApplicationAuthorizer authorizer, LoanRepository loans, PaymentPlanGenerator paymentPlans,
            UuidGeneratorPort uuids, ClockPort clock, OutboxEventsPort outbox, UnitOfWorkPort unitOfWork) {
        return new AcceptLoanHandler(authorizer, loans, paymentPlans, uuids, clock, outbox, unitOfWork);
    }

    @Bean
    CommandHandler<ReportPaymentCommand, PaymentRegistered> reportPaymentHandler(
            ApplicationAuthorizer authorizer, LoanRepository loans, ReportedPaymentRepository payments,
            UuidGeneratorPort uuids, ClockPort clock, OutboxEventsPort outbox, UnitOfWorkPort unitOfWork) {
        return new ReportPaymentHandler(authorizer, loans, payments, uuids, clock, outbox, unitOfWork);
    }

    @Bean
    CommandHandler<ApprovePaymentCommand, PaymentProcessed> approvePaymentHandler(
            ApplicationAuthorizer authorizer, LoanRepository loans, ReportedPaymentRepository payments,
            PaymentPlanGenerator paymentPlans, UuidGeneratorPort uuids, ClockPort clock,
            OutboxEventsPort outbox, UnitOfWorkPort unitOfWork, FinancialLedgerPort financialLedger,
            PaymentAllocationValidationPort allocationValidation) {
        return new ApprovePaymentHandler(authorizer, loans, payments, paymentPlans, uuids, clock, outbox, unitOfWork,
                financialLedger, allocationValidation);
    }

    @Bean
    CommandHandler<RejectPaymentCommand, PaymentProcessed> rejectPaymentHandler(
            ApplicationAuthorizer authorizer, ReportedPaymentRepository payments, LoanRepository loans,
            ClockPort clock, OutboxEventsPort outbox, UnitOfWorkPort unitOfWork) {
        return new RejectPaymentHandler(authorizer, payments, loans, clock, outbox, unitOfWork);
    }

    @Bean
    CommandHandler<ReversePaymentCommand, PaymentProcessed> reversePaymentHandler(
            ApplicationAuthorizer authorizer, ReportedPaymentRepository payments, LoanRepository loans,
            ClockPort clock, OutboxEventsPort outbox, UnitOfWorkPort unitOfWork, FinancialLedgerPort financialLedger,
            PaymentAllocationValidationPort allocationValidation) {
        return new ReversePaymentHandler(authorizer, payments, loans, clock, outbox, unitOfWork, financialLedger,
                allocationValidation);
    }

    @Bean
    QueryHandler<ListLenderLoansQuery, List<LoanSummary>> listLenderLoansHandler(
            ApplicationAuthorizer authorizer, LoanReadModelPort loans) {
        return new ListLenderLoansHandler(authorizer, loans);
    }

    @Bean
    QueryHandler<ListPayerLoansQuery, List<LoanSummary>> listPayerLoansHandler(
            ApplicationAuthorizer authorizer, LoanReadModelPort loans) {
        return new ListPayerLoansHandler(authorizer, loans);
    }

    @Bean
    QueryHandler<ListPendingPaymentsQuery, Page<PaymentSummary>> listPendingPaymentsHandler(
            ApplicationAuthorizer authorizer, LoanRepository loans, PaymentReadModelPort payments) {
        return new ListPendingPaymentsHandler(authorizer, loans, payments);
    }
}
