package com.project.backend.application.port.out;

import com.project.backend.application.dto.StoredObjectRecord;
import com.project.backend.application.dto.StoredObjectUpload;

import java.util.Optional;
import java.util.UUID;

public interface StoredObjectPort {
    void stage(StoredObjectUpload upload);

    void markSafe(UUID storedObjectId);

    void markRejected(UUID storedObjectId);

    void markDeleted(UUID storedObjectId);

    Optional<StoredObjectRecord> findByIdForUpdate(UUID storedObjectId);
}
