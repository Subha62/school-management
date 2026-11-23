package com.schoolmanagement.file;

import java.io.IOException;

/** A key is known to the database but the file store has no content for it. */
public class StoredFileMissingException extends IOException {

    public StoredFileMissingException(String key) {
        super("Stored file not found: " + key);
    }
}
