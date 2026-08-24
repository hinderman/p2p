package com.project.backend.application.command;

import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.dto.ClaimedLoanInvitation;
import com.project.backend.application.dto.ScheduledLoanInvitation;
import com.project.backend.application.port.out.AuthenticationTokenIssuerPort;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.LoanInvitationPort;
import com.project.backend.application.port.out.OutboxEventsPort;
import com.project.backend.application.port.out.PasswordHashingPort;
import com.project.backend.application.port.out.PasswordVerifierPort;
import com.project.backend.application.port.out.UnitOfWorkPort;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.domain.event.DomainEvent;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.PersonStatus;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.NonBusinessDayAdjustment;
import com.project.backend.domain.loan.PaymentFrequency;
import com.project.backend.domain.loan.PaymentScheduleRule;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.service.StandardPaymentPlanGenerator;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.InterestRate;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompletePayerOnboardingHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void consumes_the_invitation_activates_the_payer_and_accepts_the_exact_loan() {
        EmailAddress payerEmail = new EmailAddress("payer@example.com");
        Person payer = Person.createPending(new PersonId(UUID.randomUUID()), payerEmail, NOW.minusSeconds(60));
        LoanTerms terms = terms();
        Loan loan = Loan.create(new LoanId(UUID.randomUUID()), new PersonId(UUID.randomUUID()), payer.id(), terms, NOW.minusSeconds(60));
        InMemoryPeople people = new InMemoryPeople(payer);
        InMemoryAccounts accounts = new InMemoryAccounts();
        InMemoryLoans loans = new InMemoryLoans(loan);
        RecordingInvitations invitations = new RecordingInvitations(new ClaimedLoanInvitation(
                new LoanInvitationId(UUID.randomUUID()), loan.id(), terms.id(), payerEmail));
        CompletePayerOnboardingHandler handler = new CompletePayerOnboardingHandler(
                invitations, people, accounts, loans, password -> new PasswordHash("argon2id-hash"),
                (password, hash) -> false, new StandardPaymentPlanGenerator(), sessionIssuer(), UUID::randomUUID,
                () -> NOW, noOpOutbox(), directUnitOfWork());

        AuthenticatedSession session = handler.execute(new CompletePayerOnboardingCommand(
                "one-time-token", "a-secure-password".toCharArray(), "127.0.0.1", "test-agent"));

        assertEquals(LoanStatus.ACTIVE, loan.status());
        assertEquals(LoanTermStatus.ACCEPTED, terms.status());
        assertEquals(PersonStatus.ACTIVE, payer.status());
        assertNotNull(accounts.account);
        assertEquals(UserAccountStatus.ACTIVE, accounts.account.status());
        assertEquals(Set.of(UserRole.PAYER), accounts.account.roles());
        assertEquals(accounts.account.id(), session.userAccountId());
        assertEquals(accounts.account.id(), invitations.acceptedBy);
    }

    private static LoanTerms terms() {
        return new LoanTerms(new LoanTermId(UUID.randomUUID()), 1, money("1000.0000"),
                new InterestRate(new BigDecimal("2.00000000")), RatePeriod.MONTHLY_NOMINAL,
                InterestCalculationMethod.SIMPLE, DayCountBasis.THIRTY_360, AmortizationMethod.FIXED_PAYMENT,
                CapitalPrepaymentPolicy.REDUCE_PAYMENT, 2, LocalDate.of(2026, 9, 19), ZoneId.of("UTC"),
                new PaymentScheduleRule(PaymentFrequency.MONTHLY, null, Set.of(19), NonBusinessDayAdjustment.NO_ADJUSTMENT),
                LoanTermStatus.PROPOSED);
    }

    private static AuthenticationTokenIssuerPort sessionIssuer() {
        return (account, issuedAt) -> new AuthenticatedSession(account.id(), account.personId(), account.roles(),
                "access", "refresh", issuedAt.plusSeconds(900));
    }

    private static UnitOfWorkPort directUnitOfWork() {
        return new UnitOfWorkPort() {
            @Override public <T> T execute(Supplier<T> operation) { return operation.get(); }
        };
    }

    private static OutboxEventsPort noOpOutbox() {
        return new OutboxEventsPort() {
            @Override public void enqueue(List<DomainEvent> events) { }
            @Override public void enqueueInvitationEmail(
                    com.project.backend.application.dto.InvitationEmailMessage invitation, Instant occurredAt) { }
        };
    }

    private static Money money(String amount) { return new Money(new BigDecimal(amount), "COP"); }

    private static final class InMemoryPeople implements PersonRepository {
        private Person person;
        private InMemoryPeople(Person person) { this.person = person; }
        @Override public Optional<Person> findById(PersonId id) { return person.id().equals(id) ? Optional.of(person) : Optional.empty(); }
        @Override public Optional<Person> findByEmail(EmailAddress email) { return person.primaryEmail().equals(email) ? Optional.of(person) : Optional.empty(); }
        @Override public Person save(Person aggregate) { person = aggregate; return aggregate; }
        @Override public void delete(PersonId id) { }
        @Override public boolean exists(PersonId id) { return person.id().equals(id); }
    }

    private static final class InMemoryAccounts implements UserAccountRepository {
        private UserAccount account;
        @Override public Optional<UserAccount> findById(UserAccountId id) { return account == null || !account.id().equals(id) ? Optional.empty() : Optional.of(account); }
        @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.ofNullable(account); }
        @Override public UserAccount save(UserAccount aggregate) { account = aggregate; return aggregate; }
        @Override public void delete(UserAccountId id) { }
        @Override public boolean exists(UserAccountId id) { return account != null && account.id().equals(id); }
    }

    private static final class InMemoryLoans implements LoanRepository {
        private Loan loan;
        private InMemoryLoans(Loan loan) { this.loan = loan; }
        @Override public Optional<Loan> findById(LoanId id) { return loan.id().equals(id) ? Optional.of(loan) : Optional.empty(); }
        @Override public List<Loan> findActiveByLender(PersonId lenderId) { return List.of(); }
        @Override public List<Loan> findActiveByPayer(PersonId payerId) { return List.of(); }
        @Override public Loan save(Loan aggregate) { loan = aggregate; return aggregate; }
        @Override public void delete(LoanId id) { }
        @Override public boolean exists(LoanId id) { return loan.id().equals(id); }
    }

    private static final class RecordingInvitations implements LoanInvitationPort {
        private final ClaimedLoanInvitation invitation;
        private boolean claimed;
        private UserAccountId acceptedBy;
        private RecordingInvitations(ClaimedLoanInvitation invitation) { this.invitation = invitation; }
        @Override public ScheduledLoanInvitation scheduleInvitation(
                LoanId loanId, LoanTermId loanTermId, EmailAddress recipient, Instant createdAt) {
            throw new UnsupportedOperationException("Not used by this test");
        }
        @Override public Optional<ClaimedLoanInvitation> claimForPayerOnboarding(String rawToken, Instant acceptedAt) {
            if (claimed) return Optional.empty();
            claimed = true;
            return Optional.of(invitation);
        }
        @Override public void recordPayerAcceptance(ClaimedLoanInvitation invitation, UserAccountId accountId, String sourceIp, String userAgent, Instant acceptedAt) {
            acceptedBy = accountId;
        }
    }
}
