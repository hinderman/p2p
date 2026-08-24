package com.project.backend.infrastructure.persistence.outbox;

import com.project.backend.application.dto.InvitationEmailMessage;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.infrastructure.notification.InvitationEmailPayloadCipher;
import com.project.backend.infrastructure.notification.InvitationEmailProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcOutboxEventsAdapterTest {

    @Test
    void persists_an_encrypted_invitation_request_without_the_raw_token_or_email() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        InvitationEmailPayloadCipher cipher = new InvitationEmailPayloadCipher(properties());
        JdbcOutboxEventsAdapter adapter = new JdbcOutboxEventsAdapter(jdbcTemplate, cipher);
        String rawToken = "single-use-token";

        adapter.enqueueInvitationEmail(new InvitationEmailMessage(new LoanInvitationId(UUID.randomUUID()),
                new EmailAddress("payer@example.com"), rawToken), Instant.parse("2026-08-20T12:00:00Z"));

        var payload = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("LoanInvitationEmailRequested"), payload.capture());
        String serializedPayload = (String) payload.getValue()[2];
        assertFalse(serializedPayload.contains(rawToken));
        assertFalse(serializedPayload.contains("payer@example.com"));
        assertTrue(cipher.decrypt(serializedPayload).rawToken().equals(rawToken));
    }

    private static InvitationEmailProperties properties() {
        return new InvitationEmailProperties(true, "no-reply@project.local", "Project",
                URI.create("http://localhost:5173"), "/onboarding/payer", Duration.ofSeconds(1), 3,
                Base64.getEncoder().encodeToString(new byte[32]));
    }
}
