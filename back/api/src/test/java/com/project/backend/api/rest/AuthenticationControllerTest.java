package com.project.backend.api.rest;

import com.project.backend.api.exception.ApiExceptionHandler;
import com.project.backend.application.command.SignInCommand;
import com.project.backend.application.command.RefreshSessionCommand;
import com.project.backend.application.command.RevokeSessionsCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.RateLimitExceededException;
import com.project.backend.application.dto.LoanCreated;
import com.project.backend.application.dto.LoanSummary;
import com.project.backend.application.dto.Page;
import com.project.backend.application.dto.PaymentSummary;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.in.query.QueryHandler;
import com.project.backend.application.query.ListLenderLoansQuery;
import com.project.backend.application.query.ListPayerLoansQuery;
import com.project.backend.application.query.ListPendingPaymentsQuery;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
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
        @SuppressWarnings("unchecked")
        CommandHandler<SignInCommand, AuthenticatedSession> signIn = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        CommandHandler<RefreshSessionCommand, AuthenticatedSession> refresh = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        CommandHandler<RevokeSessionsCommand, Void> revoke = mock(CommandHandler.class);
        UUID accountId = UUID.randomUUID();
        when(signIn.execute(any())).thenReturn(new AuthenticatedSession(new UserAccountId(accountId), new PersonId(UUID.randomUUID()),
                Set.of(UserRole.LENDER), "access-token", "refresh-token", Instant.parse("2026-08-19T12:00:00Z")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(signIn, refresh, revoke, new CurrentAccountResolver()))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lender@example.com\",\"password\":\"secure-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
        verify(signIn).execute(any(SignInCommand.class));
    }

    @Test
    void returns_a_validation_problem_for_an_invalid_sign_in_request() throws Exception {
        @SuppressWarnings("unchecked")
        CommandHandler<SignInCommand, AuthenticatedSession> signIn = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        CommandHandler<RefreshSessionCommand, AuthenticatedSession> refresh = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        CommandHandler<RevokeSessionsCommand, Void> revoke = mock(CommandHandler.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(signIn, refresh, revoke, new CurrentAccountResolver()))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.violations.email").exists());
    }

    @Test
    void rejects_a_protected_route_without_a_principal() throws Exception {
        @SuppressWarnings("unchecked")
        CommandHandler<com.project.backend.application.command.CreateLoanCommand, LoanCreated> createLoan = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        QueryHandler<ListLenderLoansQuery, List<LoanSummary>> lenderLoans = mock(QueryHandler.class);
        @SuppressWarnings("unchecked")
        QueryHandler<ListPayerLoansQuery, List<LoanSummary>> payerLoans = mock(QueryHandler.class);
        @SuppressWarnings("unchecked")
        QueryHandler<ListPendingPaymentsQuery, Page<PaymentSummary>> pendingPayments = mock(QueryHandler.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LoanController(new CurrentAccountResolver(), createLoan,
                        lenderLoans, payerLoans, pendingPayments))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(get("/api/v1/loans/lender"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Test
    void returns_a_rate_limit_problem_when_sign_in_is_throttled() throws Exception {
        @SuppressWarnings("unchecked")
        CommandHandler<SignInCommand, AuthenticatedSession> signIn = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        CommandHandler<RefreshSessionCommand, AuthenticatedSession> refresh = mock(CommandHandler.class);
        @SuppressWarnings("unchecked")
        CommandHandler<RevokeSessionsCommand, Void> revoke = mock(CommandHandler.class);
        when(signIn.execute(any())).thenThrow(new RateLimitExceededException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthenticationController(signIn, refresh, revoke, new CurrentAccountResolver()))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/auth/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lender@example.com\",\"password\":\"secure-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limit_exceeded"));
    }
}
