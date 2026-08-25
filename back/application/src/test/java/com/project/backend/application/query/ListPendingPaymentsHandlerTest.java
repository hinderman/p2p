package com.project.backend.application.query;

import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PageRequest;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.exception.AccessDeniedException;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.out.PaymentReadModelPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.Loan;
import com.project.backend.domain.loan.LoanTermStatus;
import com.project.backend.domain.loan.LoanTerms;
import com.project.backend.domain.loan.NonBusinessDayAdjustment;
import com.project.backend.domain.loan.PaymentFrequency;
import com.project.backend.domain.loan.PaymentScheduleRule;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.payment.ReportedPaymentStatus;
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.InterestRate;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.LoanTermId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.ReportedPaymentId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListPendingPaymentsHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final PersonId LENDER = new PersonId(UUID.randomUUID());
    private static final PersonId PAYER = new PersonId(UUID.randomUUID());

    @Test
    void forwards_the_requested_page_to_the_read_model_instead_of_slicing_in_memory() {
        Loan loan = loan();
        UserAccount lender = account(LENDER, UserRole.LENDER);
        RecordingReadModel readModel = new RecordingReadModel(Page.of(List.of(payment()), new PageRequest(2, 5), 11));
        var handler = handler(lender, loan, readModel);

        Page<PaymentSummary> page = handler.execute(
                new ListPendingPaymentsQuery(lender.id(), loan.id(), new PageRequest(2, 5)));

        assertEquals(loan.id(), readModel.loanId);
        assertEquals(ReportedPaymentStatus.PENDING_REVIEW, readModel.status);
        assertEquals(new PageRequest(2, 5), readModel.pageRequest);
        assertEquals(1, page.content().size());
        assertEquals(11L, page.totalElements());
        assertEquals(3, page.totalPages());
    }

    @Test
    void rejects_an_account_that_does_not_own_the_loan_before_reading_any_page() {
        Loan loan = loan();
        UserAccount intruder = account(new PersonId(UUID.randomUUID()), UserRole.LENDER);
        RecordingReadModel readModel = new RecordingReadModel(Page.empty(new PageRequest(0, 20), 0));
        var handler = handler(intruder, loan, readModel);

        assertThrows(AccessDeniedException.class, () -> handler.execute(
                new ListPendingPaymentsQuery(intruder.id(), loan.id(), new PageRequest(0, 20))));
        assertNull(readModel.pageRequest);
    }

    @Test
    void fails_when_the_loan_does_not_exist() {
        UserAccount lender = account(LENDER, UserRole.LENDER);
        RecordingReadModel readModel = new RecordingReadModel(Page.empty(new PageRequest(0, 20), 0));
        var handler = handler(lender, loan(), readModel);

        assertThrows(ResourceNotFoundException.class, () -> handler.execute(new ListPendingPaymentsQuery(
                lender.id(), new LoanId(UUID.randomUUID()), new PageRequest(0, 20))));
    }

    private static ListPendingPaymentsHandler handler(UserAccount account, Loan loan, PaymentReadModelPort readModel) {
        return new ListPendingPaymentsHandler(
                new ApplicationAuthorizer(new SingleAccount(account)), new SingleLoan(loan), readModel);
    }

    private static UserAccount account(PersonId personId, UserRole role) {
        return UserAccount.rehydrate(new UserAccountId(UUID.randomUUID()), personId,
                new PasswordHash("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA"), UserAccountStatus.ACTIVE,
                Set.of(role), 0, NOW, NOW);
    }

    private static Loan loan() {
        return Loan.create(new LoanId(UUID.randomUUID()), LENDER, PAYER, terms(), NOW);
    }

    private static LoanTerms terms() {
        return new LoanTerms(new LoanTermId(UUID.randomUUID()), 1, new Money(new BigDecimal("1000.0000"), "COP"),
                new InterestRate(new BigDecimal("2.00000000")), RatePeriod.MONTHLY_NOMINAL,
                InterestCalculationMethod.SIMPLE, DayCountBasis.THIRTY_360, AmortizationMethod.FIXED_PAYMENT,
                CapitalPrepaymentPolicy.REDUCE_PAYMENT, 2, LocalDate.of(2026, 9, 25), ZoneId.of("UTC"),
                new PaymentScheduleRule(PaymentFrequency.MONTHLY, null, Set.of(25), NonBusinessDayAdjustment.NO_ADJUSTMENT),
                LoanTermStatus.PROPOSED);
    }

    private static PaymentSummary payment() {
        return new PaymentSummary(new ReportedPaymentId(UUID.randomUUID()), new LoanId(UUID.randomUUID()),
                ReportedPaymentStatus.PENDING_REVIEW, new Money(new BigDecimal("100.0000"), "COP"), null,
                LocalDate.of(2026, 8, 25));
    }

    private static final class RecordingReadModel implements PaymentReadModelPort {
        private final Page<PaymentSummary> result;
        private LoanId loanId;
        private ReportedPaymentStatus status;
        private PageRequest pageRequest;

        private RecordingReadModel(Page<PaymentSummary> result) {
            this.result = result;
        }

        @Override
        public Page<PaymentSummary> findByLoanAndStatus(LoanId loanId, ReportedPaymentStatus status, PageRequest pageRequest) {
            this.loanId = loanId;
            this.status = status;
            this.pageRequest = pageRequest;
            return result;
        }
    }

    private record SingleAccount(UserAccount account) implements UserAccountRepository {
        @Override public Optional<UserAccount> findById(UserAccountId id) {
            return account.id().equals(id) ? Optional.of(account) : Optional.empty();
        }
        @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.of(account); }
        @Override public UserAccount save(UserAccount aggregate) { return aggregate; }
        @Override public void delete(UserAccountId id) { }
        @Override public boolean exists(UserAccountId id) { return account.id().equals(id); }
    }

    private record SingleLoan(Loan loan) implements LoanRepository {
        @Override public Optional<Loan> findById(LoanId id) {
            return loan.id().equals(id) ? Optional.of(loan) : Optional.empty();
        }
        @Override public List<Loan> findActiveByLender(PersonId lenderId) { return List.of(); }
        @Override public List<Loan> findActiveByPayer(PersonId payerId) { return List.of(); }
        @Override public Loan save(Loan aggregate) { return aggregate; }
        @Override public void delete(LoanId id) { }
        @Override public boolean exists(LoanId id) { return loan.id().equals(id); }
    }
}
