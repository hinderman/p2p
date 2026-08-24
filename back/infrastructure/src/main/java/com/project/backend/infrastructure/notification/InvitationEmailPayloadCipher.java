package com.project.backend.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.backend.application.dto.InvitationEmailMessage;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.LoanInvitationId;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** Encrypts the complete email request before it is persisted in the transactional outbox. */
@Component
public final class InvitationEmailPayloadCipher {
    private static final int NONCE_LENGTH = 12;
    private static final int AUTHENTICATION_TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SecretKeySpec encryptionKey;

    public InvitationEmailPayloadCipher(InvitationEmailProperties properties) {
        this.encryptionKey = new SecretKeySpec(properties.decodedOutboxEncryptionKey(), "AES");
    }

    public String encrypt(InvitationEmailMessage invitation) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            SECURE_RANDOM.nextBytes(nonce);
            byte[] clearText = JSON.writeValueAsBytes(new ClearPayload(
                    invitation.invitationId().value(), invitation.recipientEmail().value(), invitation.rawToken()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
            byte[] cipherText = cipher.doFinal(clearText);
            return JSON.writeValueAsString(new EncryptedPayload(1,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
                    Base64.getUrlEncoder().withoutPadding().encodeToString(cipherText)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt invitation-email outbox payload", exception);
        }
    }

    public InvitationEmailMessage decrypt(String serializedPayload) {
        try {
            EncryptedPayload encrypted = JSON.readValue(serializedPayload, EncryptedPayload.class);
            if (encrypted.version() != 1) {
                throw new IllegalArgumentException("Unsupported invitation-email payload version");
            }
            byte[] nonce = Base64.getUrlDecoder().decode(encrypted.nonce());
            if (nonce.length != NONCE_LENGTH) {
                throw new IllegalArgumentException("Invalid invitation-email payload nonce");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
            ClearPayload clear = JSON.readValue(cipher.doFinal(Base64.getUrlDecoder().decode(encrypted.cipherText())), ClearPayload.class);
            return new InvitationEmailMessage(new LoanInvitationId(clear.invitationId()),
                    new EmailAddress(clear.recipientEmail()), clear.rawToken());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid encrypted invitation-email outbox payload", exception);
        }
    }

    private record ClearPayload(UUID invitationId, String recipientEmail, String rawToken) { }

    private record EncryptedPayload(int version, String nonce, String cipherText) { }
}
