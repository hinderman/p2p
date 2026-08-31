package com.project.backend.infrastructure.storage;

import com.project.backend.application.dto.StoredObjectRecord;
import com.project.backend.application.dto.StoredObjectScanStatus;
import com.project.backend.application.dto.StoredObjectUpload;
import com.project.backend.application.port.out.StoredObjectPort;
import com.project.backend.domain.valueobject.UserAccountId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;

/** Local, non-public object storage with quarantine separated from safe files. */
@Component
public final class LocalStoredObjectAdapter implements StoredObjectPort {
    private final JdbcTemplate jdbcTemplate;
    private final Path root;

    public LocalStoredObjectAdapter(JdbcTemplate jdbcTemplate, PaymentProofStorageProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.root = properties.root();
    }

    @Override
    public void stage(StoredObjectUpload upload) {
        Path quarantine = quarantinePath(upload.id());
        try {
            Files.createDirectories(quarantine.getParent());
            byte[] content = upload.content();
            Files.write(quarantine, content);
            jdbcTemplate.update("""
                    INSERT INTO loans.stored_objects
                    (stored_object_id, storage_key, original_name, content_type, size_bytes, sha256,
                     scan_status, uploaded_by_user_account_id, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                    """, upload.id(), storageKey(upload.id()), upload.originalName(), upload.contentType(),
                    content.length, upload.sha256(), upload.uploadedBy().value(), timestamp(upload.createdAt()));
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(quarantine);
            throw new IllegalStateException("The payment proof could not be staged", exception);
        }
    }

    @Override
    public void markSafe(UUID storedObjectId) {
        Path quarantine = quarantinePath(storedObjectId);
        Path destination = safePath(storedObjectId);
        try {
            Files.createDirectories(destination.getParent());
            move(quarantine, destination);
            int updated = jdbcTemplate.update("""
                    UPDATE loans.stored_objects SET scan_status = 'SAFE'
                    WHERE stored_object_id = ? AND scan_status = 'PENDING'
                    """, storedObjectId);
            if (updated != 1) throw new IllegalStateException("The staged payment proof is unavailable");
        } catch (IOException | RuntimeException exception) {
            if (Files.exists(destination) && !Files.exists(quarantine)) {
                try {
                    Files.createDirectories(quarantine.getParent());
                    move(destination, quarantine);
                } catch (IOException ignored) {
                    // The database remains PENDING, so the object cannot be attached even if compensation fails.
                }
            }
            throw new IllegalStateException("The payment proof could not be released from quarantine", exception);
        }
    }

    @Override
    public void markRejected(UUID storedObjectId) {
        deleteQuietly(quarantinePath(storedObjectId));
        jdbcTemplate.update("""
                UPDATE loans.stored_objects SET scan_status = 'REJECTED'
                WHERE stored_object_id = ? AND scan_status = 'PENDING'
                """, storedObjectId);
    }

    @Override
    public void markDeleted(UUID storedObjectId) {
        deleteQuietly(quarantinePath(storedObjectId));
        jdbcTemplate.update("""
                UPDATE loans.stored_objects SET scan_status = 'DELETED'
                WHERE stored_object_id = ? AND scan_status = 'PENDING'
                """, storedObjectId);
    }

    @Override
    public Optional<StoredObjectRecord> findByIdForUpdate(UUID storedObjectId) {
        List<StoredObjectRecord> records = jdbcTemplate.query("""
                SELECT object.stored_object_id, object.uploaded_by_user_account_id, object.sha256,
                       object.scan_status, EXISTS (
                           SELECT 1 FROM loans.payment_evidence evidence
                           WHERE evidence.stored_object_id = object.stored_object_id
                       ) AS attached
                FROM loans.stored_objects object
                WHERE object.stored_object_id = ?
                FOR UPDATE
                """, (resultSet, rowNumber) -> new StoredObjectRecord(
                resultSet.getObject("stored_object_id", UUID.class),
                new UserAccountId(resultSet.getObject("uploaded_by_user_account_id", UUID.class)),
                resultSet.getString("sha256"),
                StoredObjectScanStatus.valueOf(resultSet.getString("scan_status")),
                resultSet.getBoolean("attached")), storedObjectId);
        return records.stream().findFirst();
    }

    private String storageKey(UUID id) {
        return "payment-proofs/" + id.toString().substring(0, 2) + "/" + id;
    }

    private Path safePath(UUID id) {
        return resolveInsideRoot(storageKey(id));
    }

    private Path quarantinePath(UUID id) {
        return resolveInsideRoot(".quarantine/" + id);
    }

    private Path resolveInsideRoot(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("The storage key escapes the configured root");
        return resolved;
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Metadata state still prevents a non-safe object from being attached.
        }
    }
}
