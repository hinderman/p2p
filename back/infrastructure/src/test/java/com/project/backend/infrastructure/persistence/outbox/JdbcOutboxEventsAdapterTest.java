package com.project.backend.infrastructure.persistence.outbox;

import com.project.backend.application.dto.OutboundEmailKind;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.infrastructure.notification.OutboundEmailPayloadCipher;
import com.project.backend.infrastructure.notification.OutboundEmailProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcOutboxEventsAdapterTest {

    @Test
    void persists_an_encrypted_invitation_request_without_the_raw_token_or_email() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(properties());
        JdbcOutboxEventsAdapter adapter = new JdbcOutboxEventsAdapter(jdbcTemplate, cipher);
        String rawToken = "single-use-token";

        adapter.enqueueEmail(OutboundEmailMessage.loanInvitation(new LoanInvitationId(UUID.randomUUID()),
                new EmailAddress("payer@example.com"), rawToken), Instant.parse("2026-08-20T12:00:00Z"));

        Object[] arguments = capturedArguments(jdbcTemplate);
        assertEquals("LoanInvitationEmailRequested", arguments[3]);
        String serializedPayload = (String) arguments[4];
        assertFalse(serializedPayload.contains(rawToken));
        assertFalse(serializedPayload.contains("payer@example.com"));
        assertTrue(cipher.decrypt(serializedPayload).rawToken().equals(rawToken));
    }

    @Test
    void routes_a_verification_request_to_its_own_event_type() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(1);
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(properties());
        JdbcOutboxEventsAdapter adapter = new JdbcOutboxEventsAdapter(jdbcTemplate, cipher);

        adapter.enqueueEmail(OutboundEmailMessage.accountEmailVerification(UUID.randomUUID(),
                new EmailAddress("lender@example.com"), "verification-token"), Instant.parse("2026-08-20T12:00:00Z"));

        Object[] arguments = capturedArguments(jdbcTemplate);
        assertEquals("UserAccount", arguments[1]);
        assertEquals("AccountVerificationEmailRequested", arguments[3]);
        assertFalse(((String) arguments[4]).contains("verification-token"));
    }

    /** The publisher drains by event type, so every kind must be covered by its query. */
    @Test
    void every_message_kind_is_drained_by_the_publisher() {
        List<String> drained = OutboundEmailOutboxEvent.allEventTypes();

        assertEquals(OutboundEmailKind.values().length, drained.size());
        for (OutboundEmailKind kind : OutboundEmailKind.values()) {
            assertTrue(drained.contains(OutboundEmailOutboxEvent.eventType(kind)));
        }
    }

    private static Object[] capturedArguments(JdbcTemplate jdbcTemplate) {
        ArgumentCaptor<Object[]> payload = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), payload.capture());
        return payload.getValue();
    }

    private static OutboundEmailProperties properties() {
        return new OutboundEmailProperties(true, "no-reply@project.local", "Project",
                URI.create("http://localhost:5173"), "/onboarding/payer", "/registro/verificacion",
                Duration.ofSeconds(1), 3, Base64.getEncoder().encodeToString(new byte[32]));
    }
}
