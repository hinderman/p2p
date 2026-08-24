package com.project.backend.infrastructure.notification;

import com.project.backend.application.dto.InvitationEmailMessage;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvitationEmailPayloadCipherTest {

    @Test
    void encrypts_the_entire_sensitive_email_message_and_round_trips_it() {
        InvitationEmailPayloadCipher cipher = new InvitationEmailPayloadCipher(properties());
        InvitationEmailMessage original = new InvitationEmailMessage(new LoanInvitationId(UUID.randomUUID()),
                new EmailAddress("payer@example.com"), "single-use-token");

        String encrypted = cipher.encrypt(original);
        InvitationEmailMessage restored = cipher.decrypt(encrypted);

        assertFalse(encrypted.contains("payer@example.com"));
        assertFalse(encrypted.contains("single-use-token"));
        assertEquals(original, restored);
    }

    @Test
    void rejects_a_tampered_encrypted_payload() {
        InvitationEmailPayloadCipher cipher = new InvitationEmailPayloadCipher(properties());
        String encrypted = cipher.encrypt(new InvitationEmailMessage(new LoanInvitationId(UUID.randomUUID()),
                new EmailAddress("payer@example.com"), "single-use-token"));

        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(encrypted.substring(0, encrypted.length() - 2) + "xx"));
    }

    @Test
    void builds_a_fragment_link_for_local_onboarding() {
        InvitationLinkBuilder links = new InvitationLinkBuilder(properties());

        assertEquals("http://localhost:5173/onboarding/payer#invitationToken=single-use-token",
                links.build("single-use-token"));
    }

    private static InvitationEmailProperties properties() {
        return new InvitationEmailProperties(true, "no-reply@project.local", "Project",
                URI.create("http://localhost:5173"), "/onboarding/payer", Duration.ofSeconds(1), 3,
                Base64.getEncoder().encodeToString(new byte[32]));
    }
}
