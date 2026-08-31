package com.project.backend.api.rest;

import com.project.backend.api.exception.ApiExceptionHandler;
import com.project.backend.application.command.RegisterLenderCommand;
import com.project.backend.application.command.ResendAccountVerificationCommand;
import com.project.backend.application.command.VerifyAccountEmailCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.exception.VerificationInvalidException;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationControllerTest {
    private static final String VALID_REGISTRATION = """
            {"email":"carolina@example.com","password":"mesa verde caliente",
             "firstName":"Carolina","lastName":"Restrepo"}""";

    private final ApplicationMediator mediator = mock(ApplicationMediator.class);

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new RegistrationController(mediator))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void accepts_a_registration_and_answers_without_a_body() throws Exception {
        mockMvc().perform(post("/api/v1/registration/lender")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        ArgumentCaptor<RegisterLenderCommand> command = ArgumentCaptor.forClass(RegisterLenderCommand.class);
        verify(mediator).send(command.capture());
        assertEquals("carolina@example.com", command.getValue().email().value());
        assertEquals("Carolina", command.getValue().firstName());
    }

    /**
     * The response for an address that already has an account is byte-for-byte the
     * response for a fresh one, which is what stops the endpoint being an oracle.
     */
    @Test
    void answers_an_already_registered_address_exactly_like_a_new_one() throws Exception {
        mockMvc().perform(post("/api/v1/registration/lender")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
    }

    @Test
    void rejects_a_malformed_registration_before_reaching_the_use_case() throws Exception {
        mockMvc().perform(post("/api/v1/registration/lender").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","firstName":"","lastName":"Restrepo"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations.email").exists())
                .andExpect(jsonPath("$.violations.password").exists())
                .andExpect(jsonPath("$.violations.firstName").exists());

        verifyNoInteractions(mediator);
    }

    @Test
    void reports_a_password_that_fails_the_policy_as_a_business_rule_violation() throws Exception {
        when(mediator.send(any(RegisterLenderCommand.class)))
                .thenThrow(new DomainRuleViolation("The password is too common; choose one that is harder to guess"));

        mockMvc().perform(post("/api/v1/registration/lender")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("business_rule_violation"));
    }

    @Test
    void reports_a_throttled_registration_as_too_many_requests() throws Exception {
        when(mediator.send(any(RegisterLenderCommand.class))).thenThrow(new RateLimitExceededException());

        mockMvc().perform(post("/api/v1/registration/lender")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTRATION))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit_exceeded"));
    }

    @Test
    void returns_a_session_when_the_verification_link_is_redeemed() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(mediator.send(any(VerifyAccountEmailCommand.class))).thenReturn(new AuthenticatedSession(
                new UserAccountId(accountId), new PersonId(UUID.randomUUID()), Set.of(UserRole.LENDER),
                "access-token", "refresh-token", Instant.parse("2026-08-31T12:15:00Z")));

        mockMvc().perform(post("/api/v1/registration/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"single-use-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void returns_a_generic_problem_for_a_spent_or_unknown_verification_link() throws Exception {
        when(mediator.send(any(VerifyAccountEmailCommand.class))).thenThrow(new VerificationInvalidException());

        mockMvc().perform(post("/api/v1/registration/verification").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"single-use-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("verification_invalid"));
    }

    @Test
    void accepts_a_resend_request_without_revealing_whether_it_sent_anything() throws Exception {
        mockMvc().perform(post("/api/v1/registration/verification/resend")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"carolina@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(mediator).send(any(ResendAccountVerificationCommand.class));
    }
}
