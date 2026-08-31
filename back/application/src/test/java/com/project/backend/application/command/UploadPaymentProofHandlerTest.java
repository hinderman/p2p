package com.project.backend.application.command;

import com.project.backend.application.dto.StoredObjectRecord;
import com.project.backend.application.dto.StoredObjectScanStatus;
import com.project.backend.application.dto.StoredObjectUpload;
import com.project.backend.application.dto.MalwareScanResult;
import com.project.backend.application.exception.FileScanUnavailableException;
import com.project.backend.application.exception.InvalidPaymentProofException;
import com.project.backend.application.exception.MalwareDetectedException;
import com.project.backend.application.port.out.MalwareScannerPort;
import com.project.backend.application.port.out.StoredObjectPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.PasswordHash;
import com.project.backend.domain.identity.UserAccount;
import com.project.backend.domain.identity.UserRole;
import com.project.backend.domain.repository.UserAccountRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;
import com.project.backend.domain.valueobject.UserAccountId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadPaymentProofHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final UUID OBJECT_ID = UUID.fromString("d98d8980-5be6-4439-ab0d-a12da2a9da5b");
    private static final UserAccountId ACCOUNT_ID = new UserAccountId(UUID.fromString("07046205-65a7-442c-99bd-9a4265b50344"));
    private static final byte[] PDF = "%PDF-1.7 payment proof".getBytes(StandardCharsets.US_ASCII);

    @Test
    void quarantines_scans_and_releases_a_valid_pdf() {
        RecordingStorage storage = new RecordingStorage();
        var handler = handler(storage, content -> MalwareScanResult.SAFE);

        var result = handler.execute(new UploadPaymentProofCommand(
                ACCOUNT_ID, "transferencia.pdf", "application/pdf", PDF));

        assertEquals(OBJECT_ID, result.storedObjectId());
        assertEquals("application/pdf", result.contentType());
        assertEquals(StoredObjectScanStatus.SAFE, storage.status);
        assertArrayEquals(PDF, storage.upload.content());
        assertEquals("8b268611e04f020fb303d2c1c40ea880b191b92232e0f7d791a9d0aed1b74156", result.sha256());
    }

    @Test
    void rejects_content_that_only_claims_to_be_a_pdf() {
        RecordingStorage storage = new RecordingStorage();

        assertThrows(InvalidPaymentProofException.class, () -> handler(storage, content -> MalwareScanResult.SAFE)
                .execute(new UploadPaymentProofCommand(ACCOUNT_ID, "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8))));
        assertEquals(null, storage.upload);
    }

    @Test
    void retains_a_rejected_status_and_never_releases_malware() {
        RecordingStorage storage = new RecordingStorage();

        assertThrows(MalwareDetectedException.class, () -> handler(storage, content -> MalwareScanResult.MALWARE)
                .execute(new UploadPaymentProofCommand(ACCOUNT_ID, "proof.pdf", "application/pdf", PDF)));
        assertEquals(StoredObjectScanStatus.REJECTED, storage.status);
    }

    @Test
    void fails_closed_when_the_scanner_is_unavailable() {
        RecordingStorage storage = new RecordingStorage();

        assertThrows(FileScanUnavailableException.class, () -> handler(storage, content -> MalwareScanResult.UNAVAILABLE)
                .execute(new UploadPaymentProofCommand(ACCOUNT_ID, "proof.pdf", "application/pdf", PDF)));
        assertEquals(StoredObjectScanStatus.DELETED, storage.status);
    }

    private static UploadPaymentProofHandler handler(StoredObjectPort storage, MalwareScannerPort scanner) {
        UserAccount account = UserAccount.createPending(ACCOUNT_ID, new PersonId(UUID.randomUUID()),
                new PasswordHash("argon2id:test"), Set.of(UserRole.PAYER), NOW.minusSeconds(60));
        account.activate(NOW.minusSeconds(30));
        UserAccountRepository accounts = new UserAccountRepository() {
            @Override public Optional<UserAccount> findById(UserAccountId id) { return ACCOUNT_ID.equals(id) ? Optional.of(account) : Optional.empty(); }
            @Override public Optional<UserAccount> findByEmail(EmailAddress email) { return Optional.empty(); }
            @Override public UserAccount save(UserAccount aggregate) { return aggregate; }
            @Override public void delete(UserAccountId id) { }
            @Override public boolean exists(UserAccountId id) { return ACCOUNT_ID.equals(id); }
        };
        return new UploadPaymentProofHandler(new ApplicationAuthorizer(accounts), storage, scanner, () -> OBJECT_ID, () -> NOW);
    }

    private static final class RecordingStorage implements StoredObjectPort {
        private StoredObjectUpload upload;
        private StoredObjectScanStatus status;
        @Override public void stage(StoredObjectUpload upload) { this.upload = upload; status = StoredObjectScanStatus.PENDING; }
        @Override public void markSafe(UUID storedObjectId) { status = StoredObjectScanStatus.SAFE; }
        @Override public void markRejected(UUID storedObjectId) { status = StoredObjectScanStatus.REJECTED; }
        @Override public void markDeleted(UUID storedObjectId) { status = StoredObjectScanStatus.DELETED; }
        @Override public Optional<StoredObjectRecord> findByIdForUpdate(UUID storedObjectId) { return Optional.empty(); }
    }
}
