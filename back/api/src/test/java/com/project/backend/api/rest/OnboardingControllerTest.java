package com.project.backend.api.rest;

import com.project.backend.api.exception.ApiExceptionHandler;
import com.project.backend.application.command.CompletePayerOnboardingCommand;
import com.project.backend.application.dto.AuthenticatedSession;
import com.project.backend.application.exception.InvitationInvalidException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OnboardingControllerTest {

    @Test
    void redeems_a_valid_payer_invitation_and_returns_a_session() throws Exception {
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        UUID accountId = UUID.randomUUID();
        when(mediator.send(any(CompletePayerOnboardingCommand.class))).thenReturn(new AuthenticatedSession(
                new UserAccountId(accountId), new PersonId(UUID.randomUUID()), Set.of(UserRole.PAYER),
                "access-token", "refresh-token", Instant.parse("2026-08-19T12:00:00Z")));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(mediator))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/onboarding/payer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationToken\":\"single-use-token\",\"password\":\"secure-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
        verify(mediator).send(any(CompletePayerOnboardingCommand.class));
    }

    @Test
    void returns_a_generic_problem_for_an_invalid_or_consumed_invitation() throws Exception {
        ApplicationMediator mediator = mock(ApplicationMediator.class);
        when(mediator.send(any(CompletePayerOnboardingCommand.class))).thenThrow(new InvitationInvalidException());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OnboardingController(mediator))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/v1/onboarding/payer").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationToken\":\"single-use-token\",\"password\":\"secure-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invitation_invalid"));
    }
}
