package com.project.backend.api.security;

import com.project.backend.domain.identity.UserRole;
import com.project.backend.application.port.out.AccessTokenValidationPort;
import com.project.backend.application.security.AuthenticatedAccessToken;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BearerAuthenticationFilterTest {

    @Test
    void places_a_verified_account_in_the_security_context() throws Exception {
        AccessTokenValidationPort validator = mock(AccessTokenValidationPort.class);
        UUID accountId = UUID.randomUUID();
        when(validator.validate("valid-token")).thenReturn(Optional.of(new AuthenticatedAccessToken(
                new UserAccountId(accountId), new PersonId(UUID.randomUUID()), UUID.randomUUID(), Set.of(UserRole.LENDER),
                0, Instant.now(), Instant.now().plusSeconds(60))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new BearerAuthenticationFilter(validator, new ProblemAuthenticationEntryPoint()))
                .build();

        mvc.perform(get("/probe").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(accountId.toString()));
    }

    @Test
    void rejects_an_invalid_bearer_token_before_the_controller() throws Exception {
        AccessTokenValidationPort validator = mock(AccessTokenValidationPort.class);
        when(validator.validate(anyString())).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new BearerAuthenticationFilter(validator, new ProblemAuthenticationEntryPoint()))
                .build();

        mvc.perform(get("/probe").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }

    @Controller
    static class ProbeController {
        @GetMapping("/probe")
        @ResponseBody
        String currentAccount() {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
    }
}
