package com.schoolmanagement.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where uploaded files live. Callers deal in opaque, server-generated keys, never in paths or user-supplied
 * names, so an implementation can sit on a local disk, Oracle Cloud Object Storage or any S3-compatible service.
 */
public interface FileStorage {

    /**
     * Stores the content under a new key and returns it. The write is all-or-nothing: a failure leaves no
     * partial file behind.
     *
     * @param extension lower-case extension without a dot (already validated), or empty
     */
    String store(InputStream content, String extension) throws IOException;

    /** Opens a stored file; fails with {@link StoredFileMissingException} when the key is unknown. */
    InputStream open(String key) throws IOException;

    /** Removes a stored file. Removing a file that is already gone is not an error. */
    void delete(String key) throws IOException;
}
