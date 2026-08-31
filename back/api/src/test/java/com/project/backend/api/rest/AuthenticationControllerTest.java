package com.project.backend.api.rest;

import com.project.backend.api.exception.ApiExceptionHandler;
import com.project.backend.application.command.SignInCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationControllerTest {

    @Test
    void creates_a_session_from_valid_credentials() throws Exception {
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        UUID accountId = UUID.randomUUID();
        when(mediator.send(any(SignInCommand.class))).thenReturn(new AuthenticatedSession(
                new UserAccountId(accountId), new PersonId(UUID.randomUUID()),
                Set.of(UserRole.LENDER), "access-token", "refresh-token", Instant.parse("2026-08-19T12:00:00Z")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(mediator, new CurrentAccountResolver()))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lender@example.com\",\"password\":\"secure-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
        verify(mediator).send(any(SignInCommand.class));
    }

    @Test
    void returns_a_validation_problem_for_an_invalid_sign_in_request() throws Exception {
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(mediator, new CurrentAccountResolver()))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations.email").exists());
    }

    @Test
    void rejects_a_protected_route_without_a_principal() throws Exception {
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoanController(new CurrentAccountResolver(), mediator))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(get("/api/v1/loans/lender"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void returns_a_rate_limit_problem_when_sign_in_is_throttled() throws Exception {
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        when(mediator.send(any(SignInCommand.class))).thenThrow(new RateLimitExceededException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(mediator, new CurrentAccountResolver()))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lender@example.com\",\"password\":\"secure-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit_exceeded"));
    }
}
