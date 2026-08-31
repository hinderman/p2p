package com.project.backend.application.command;

import com.project.backend.application.dto.StoredObjectUpload;
import com.project.backend.application.dto.StoredPaymentProof;
import com.project.backend.application.dto.MalwareScanResult;
import com.project.backend.application.exception.FileScanUnavailableException;
import com.project.backend.application.exception.InvalidPaymentProofException;
import com.project.backend.application.exception.MalwareDetectedException;
import com.project.backend.application.port.in.command.CommandHandler;
import com.project.backend.application.port.out.ClockPort;
import com.project.backend.application.port.out.MalwareScannerPort;
import com.project.backend.application.port.out.StoredObjectPort;
import com.project.backend.application.port.out.UuidGeneratorPort;
import com.project.backend.application.security.ApplicationAuthorizer;
import com.project.backend.domain.identity.UserRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Validates, quarantines, scans, and persists one payment proof. */
public final class UploadPaymentProofHandler implements CommandHandler<UploadPaymentProofCommand, StoredPaymentProof> {
    public static final int MAX_FILE_SIZE = 15 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "application/pdf", "pdf",
            "image/png", "png",
            "image/jpeg", "jpg");

    private final ApplicationAuthorizer authorizer;
    private final StoredObjectPort storedObjects;
    private final MalwareScannerPort scanner;
    private final UuidGeneratorPort uuids;
    private final ClockPort clock;

    public UploadPaymentProofHandler(
            ApplicationAuthorizer authorizer,
            StoredObjectPort storedObjects,
            MalwareScannerPort scanner,
            UuidGeneratorPort uuids,
            ClockPort clock) {
        this.authorizer = Objects.requireNonNull(authorizer, "The authorizer is required");
        this.storedObjects = Objects.requireNonNull(storedObjects, "The stored object port is required");
        this.scanner = Objects.requireNonNull(scanner, "The malware scanner is required");
        this.uuids = Objects.requireNonNull(uuids, "The UUID generator is required");
        this.clock = Objects.requireNonNull(clock, "The clock is required");
    }

    @Override
    public Class<UploadPaymentProofCommand> requestType() {
        return UploadPaymentProofCommand.class;
    }

    @Override
    public StoredPaymentProof execute(UploadPaymentProofCommand command) {
        authorizer.requireActiveAccountWithRole(command.uploaderAccountId(), UserRole.PAYER);
        byte[] content = command.content();
        if (content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new InvalidPaymentProofException("The proof must contain between 1 byte and 15 MB");
        }

        String contentType = detectContentType(content);
        String declaredType = command.declaredContentType().toLowerCase(Locale.ROOT).trim();
        if (!declaredType.isEmpty() && !"application/octet-stream".equals(declaredType)
                && !declaredType.equals(contentType)) {
            throw new InvalidPaymentProofException("The declared media type does not match the file content");
        }
        String originalName = safeOriginalName(command.originalName(), contentType);
        String sha256 = sha256(content);
        var id = uuids.nextUuid();
        storedObjects.stage(new StoredObjectUpload(id, command.uploaderAccountId(), originalName,
                contentType, content, sha256, clock.now()));

        MalwareScanResult scanResult = scanner.scan(content);
        if (scanResult == MalwareScanResult.UNAVAILABLE) {
            storedObjects.markDeleted(id);
            throw new FileScanUnavailableException();
        }
        if (scanResult == MalwareScanResult.MALWARE) {
            storedObjects.markRejected(id);
            throw new MalwareDetectedException();
        }
        storedObjects.markSafe(id);
        return new StoredPaymentProof(id, originalName, contentType, content.length, sha256);
    }

    private static String detectContentType(byte[] content) {
        if (startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (startsWith(content, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) return "image/png";
        if (startsWith(content, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
        throw new InvalidPaymentProofException("Only PDF, PNG, and JPEG payment proofs are accepted");
    }

    private static String safeOriginalName(String value, String contentType) {
        String leaf = value.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).trim();
        leaf = leaf.replaceAll("[\\p{Cntrl}]", "_");
        if (leaf.isBlank()) leaf = "payment-proof." + ALLOWED_TYPES.get(contentType);
        if (leaf.length() > 255) leaf = leaf.substring(leaf.length() - 255);
        return leaf;
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) return false;
        }
        return true;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
