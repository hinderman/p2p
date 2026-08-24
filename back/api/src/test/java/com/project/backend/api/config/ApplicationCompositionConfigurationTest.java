package com.project.backend.api.config;

import com.project.backend.api.rest.AuthenticationController;
import com.project.backend.api.rest.CurrentAccountResolver;
import com.project.backend.api.rest.LoanController;
import com.project.backend.api.rest.OnboardingController;
import com.project.backend.api.rest.PaymentController;
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
import com.project.backend.domain.repository.LoanRepository;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.repository.ReportedPaymentRepository;
import com.project.backend.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApplicationCompositionConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationCompositionConfiguration.class)
            .withBean(UserAccountRepository.class, () -> mock(UserAccountRepository.class))
            .withBean(PersonRepository.class, () -> mock(PersonRepository.class))
            .withBean(LoanRepository.class, () -> mock(LoanRepository.class))
            .withBean(ReportedPaymentRepository.class, () -> mock(ReportedPaymentRepository.class))
            .withBean(PasswordVerifierPort.class, () -> mock(PasswordVerifierPort.class))
            .withBean(PasswordHashingPort.class, () -> mock(PasswordHashingPort.class))
            .withBean(AuthenticationTokenIssuerPort.class, () -> mock(AuthenticationTokenIssuerPort.class))
            .withBean(AuthenticationSessionPort.class, () -> mock(AuthenticationSessionPort.class))
            .withBean(ClockPort.class, () -> mock(ClockPort.class))
            .withBean(UuidGeneratorPort.class, () -> mock(UuidGeneratorPort.class))
            .withBean(LoanInvitationPort.class, () -> mock(LoanInvitationPort.class))
            .withBean(OutboxEventsPort.class, () -> mock(OutboxEventsPort.class))
            .withBean(UnitOfWorkPort.class, () -> mock(UnitOfWorkPort.class))
            .withBean(LoanReadModelPort.class, () -> mock(LoanReadModelPort.class))
            .withBean(PaymentReadModelPort.class, () -> mock(PaymentReadModelPort.class))
            .withBean(FinancialLedgerPort.class, () -> mock(FinancialLedgerPort.class))
            .withBean(PaymentAllocationValidationPort.class, () -> mock(PaymentAllocationValidationPort.class))
            .withBean(SignInRateLimitPort.class, () -> mock(SignInRateLimitPort.class))
            .withBean(CurrentAccountResolver.class)
            .withBean(AuthenticationController.class)
            .withBean(LoanController.class)
            .withBean(OnboardingController.class)
            .withBean(PaymentController.class);

    @Test
    void wires_all_use_cases_and_controllers_without_spring_annotations_inward() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AuthenticationController.class);
            assertThat(context).hasSingleBean(LoanController.class);
            assertThat(context).hasSingleBean(OnboardingController.class);
            assertThat(context).hasSingleBean(PaymentController.class);
        });
    }
}
