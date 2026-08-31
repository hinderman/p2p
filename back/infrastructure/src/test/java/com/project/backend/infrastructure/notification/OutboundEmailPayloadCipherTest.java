package com.project.backend.infrastructure.notification;

import com.project.backend.application.dto.OutboundEmailKind;
import com.project.backend.application.dto.OutboundEmailMessage;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboundEmailPayloadCipherTest {

    @Test
    void encrypts_the_entire_sensitive_email_message_and_round_trips_it() {
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(properties());
        OutboundEmailMessage original = OutboundEmailMessage.loanInvitation(
                new LoanInvitationId(UUID.randomUUID()), new EmailAddress("payer@example.com"), "single-use-token");

        String encrypted = cipher.encrypt(original);
        OutboundEmailMessage restored = cipher.decrypt(encrypted);

        assertFalse(encrypted.contains("payer@example.com"));
        assertFalse(encrypted.contains("single-use-token"));
        assertEquals(original, restored);
    }

    @Test
    void round_trips_a_verification_message_and_keeps_its_kind() {
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(properties());
        OutboundEmailMessage original = OutboundEmailMessage.accountEmailVerification(
                UUID.randomUUID(), new EmailAddress("lender@example.com"), "verification-token");

        OutboundEmailMessage restored = cipher.decrypt(cipher.encrypt(original));

        assertEquals(OutboundEmailKind.ACCOUNT_EMAIL_VERIFICATION, restored.kind());
        assertEquals(original, restored);
    }

    /** The notice carries no token, so the round trip must preserve its absence. */
    @Test
    void round_trips_a_tokenless_existing_account_notice() {
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(properties());
        OutboundEmailMessage original = OutboundEmailMessage.existingAccountNotice(
                new UserAccountId(UUID.randomUUID()), new EmailAddress("lender@example.com"));

        assertEquals(original, cipher.decrypt(cipher.encrypt(original)));
    }

    @Test
    void rejects_a_tampered_encrypted_payload() {
        OutboundEmailPayloadCipher cipher = new OutboundEmailPayloadCipher(properties());
        String encrypted = cipher.encrypt(OutboundEmailMessage.loanInvitation(
                new LoanInvitationId(UUID.randomUUID()), new EmailAddress("payer@example.com"), "single-use-token"));

        assertThrows(IllegalArgumentException.class,
                () -> cipher.decrypt(encrypted.substring(0, encrypted.length() - 2) + "xx"));
    }

    @Test
    void builds_a_fragment_link_for_local_onboarding() {
        EmailLinkBuilder links = new EmailLinkBuilder(properties());

        assertEquals("http://localhost:5173/onboarding/payer#invitationToken=single-use-token",
                links.build(OutboundEmailKind.LOAN_INVITATION, "single-use-token"));
    }

    @Test
    void builds_a_fragment_link_for_account_verification() {
        EmailLinkBuilder links = new EmailLinkBuilder(properties());

        assertEquals("http://localhost:5173/registro/verificacion#verificationToken=verification-token",
                links.build(OutboundEmailKind.ACCOUNT_EMAIL_VERIFICATION, "verification-token"));
    }

    private static OutboundEmailProperties properties() {
        return new OutboundEmailProperties(true, "no-reply@project.local", "Project",
                URI.create("http://localhost:5173"), "/onboarding/payer", "/registro/verificacion",
                Duration.ofSeconds(1), 3, Base64.getEncoder().encodeToString(new byte[32]));
    }
}
