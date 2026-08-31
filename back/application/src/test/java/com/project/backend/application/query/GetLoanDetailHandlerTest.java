package com.project.backend.application.query;

import com.project.backend.application.dto.LoanDetail;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.LoanTermsDetail;
import com.project.backend.application.exception.ResourceNotFoundException;
import com.project.backend.application.port.out.LoanReadModelPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserAccountStatus;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.loan.AmortizationMethod;
import com.project.backend.domain.loan.CapitalPrepaymentPolicy;
import com.project.backend.domain.loan.DayCountBasis;
import com.project.backend.domain.loan.InterestCalculationMethod;
import com.project.backend.domain.loan.LoanStatus;
import com.project.backend.domain.loan.RatePeriod;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanId;
import com.project.backend.domain.valueobject.Money;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetLoanDetailHandlerTest {
    @Test
    void returns_the_projection_only_for_the_authenticated_participant() {
        UserAccount account = account();
        LoanId loanId = new LoanId(UUID.randomUUID());
        LoanDetail expected = detail(loanId);
        RecordingLoans loans = new RecordingLoans(Optional.of(expected));

        LoanDetail actual = new GetLoanDetailHandler(new ApplicationAuthorizer(new SingleAccount(account)), loans)
                .execute(new GetLoanDetailQuery(account.id(), loanId));

        assertSame(expected, actual);
        org.junit.jupiter.api.Assertions.assertEquals(loanId, loans.loanId);
        org.junit.jupiter.api.Assertions.assertEquals(account.personId(), loans.participantId);
    }

    @Test
    void hides_a_loan_when_the_account_is_not_a_participant() {
        UserAccount account = account();
        RecordingLoans loans = new RecordingLoans(Optional.empty());
        LoanId loanId = new LoanId(UUID.randomUUID());

        assertThrows(ResourceNotFoundException.class, () ->
                new GetLoanDetailHandler(new ApplicationAuthorizer(new SingleAccount(account)), loans)
                        .execute(new GetLoanDetailQuery(account.id(), loanId)));
    }

    private static UserAccount account() {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        return UserAccount.rehydrate(new UserAccountId(UUID.randomUUID()), new PersonId(UUID.randomUUID()),
                new PasswordHash("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA"), UserAccountStatus.ACTIVE,
                Set.of(UserRole.LENDER), 0, now, now);
    }

    private static LoanDetail detail(LoanId loanId) {
        Money principal = new Money(new BigDecimal("1000.0000"), "COP");
        return new LoanDetail(loanId, new PersonId(UUID.randomUUID()), LoanStatus.ACTIVE, principal, principal,
                Instant.parse("2026-08-31T12:00:00Z"), new LoanTermsDetail(1, new BigDecimal("2.00000000"),
                RatePeriod.MONTHLY_EFFECTIVE, InterestCalculationMethod.SIMPLE, DayCountBasis.THIRTY_360,
                AmortizationMethod.FIXED_PAYMENT, CapitalPrepaymentPolicy.REDUCE_PAYMENT, 1,
                LocalDate.of(2026, 9, 30), "America/Bogota"), null);
    }

    private record SingleAccount(UserAccount account) implements UserAccountRepository {
        @Override public Optional<UserAccount> findById(UserAccountId id) { return account.id().equals(id) ? Optional.of(account) : Optional.empty(); }
        @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.empty(); }
        @Override public UserAccount save(UserAccount aggregate) { return aggregate; }
        @Override public void delete(UserAccountId id) { }
        @Override public boolean exists(UserAccountId id) { return account.id().equals(id); }
    }

    private static final class RecordingLoans implements LoanReadModelPort {
        private final Optional<LoanDetail> result;
        private LoanId loanId;
        private PersonId participantId;
        private RecordingLoans(Optional<LoanDetail> result) { this.result = result; }
        @Override public List<LoanSummary> findByLender(PersonId lenderId) { return List.of(); }
        @Override public List<LoanSummary> findByPayer(PersonId payerId) { return List.of(); }
        @Override public Optional<LoanDetail> findDetail(LoanId requestedLoanId, PersonId requestedParticipantId) {
            loanId = requestedLoanId; participantId = requestedParticipantId; return result;
        }
    }
}
