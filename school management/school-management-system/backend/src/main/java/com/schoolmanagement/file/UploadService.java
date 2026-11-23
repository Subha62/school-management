package com.schoolmanagement.file;

import com.schoolmanagement.file.UploadValidator.ValidatedUpload;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates and stores uploads for a database transaction. A file written for a transaction that does not commit
 * is removed again, and a file is deleted only after the transaction that removed its record has committed, so
 * the database never points at a file that is gone.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    /** The record to keep for a stored file. */
    public record StoredUpload(String key, String originalName, String contentType, long sizeBytes) {
    }

    private final FileStorage storage;
    private final UploadValidator validator;

    public UploadService(FileStorage storage, UploadValidator validator) {
        this.storage = storage;
        this.validator = validator;
    }

    /** Checks every file first, so nothing is stored when any of them is refused. */
    public List<StoredUpload> storeAll(List<MultipartFile> files) {
        List<ValidatedUpload> valid = files.stream().map(validator::validate).toList();
        List<StoredUpload> stored = new ArrayList<>();
        for (ValidatedUpload upload : valid) {
            try (InputStream in = upload.file().getInputStream()) {
                String key = storage.store(in, upload.extension());
                removeUnlessCommitted(key);
                stored.add(new StoredUpload(key, upload.displayName(), upload.contentType(),
                        upload.file().getSize()));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not store the uploaded file", e);
            }
        }
        return stored;
    }

    public InputStream open(String key) throws IOException {
        return storage.open(key);
    }

    /** Deletes the file once the surrounding transaction has committed (immediately when there is none). */
    public void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private void removeUnlessCommitted(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException e) {
            // An orphaned file is harmless to users; it is logged so an operator can clean up.
            log.warn("Could not delete stored file {}", key, e);
        }
    }
}
