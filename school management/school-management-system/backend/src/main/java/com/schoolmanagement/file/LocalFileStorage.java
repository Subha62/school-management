package com.schoolmanagement.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stores files below one root directory, in a two-character shard folder named by the first characters of the
 * generated key. Nothing a client sends ever becomes part of a path, and every key is checked against a strict
 * pattern and the resolved path against the root before the disk is touched.
 */
@Component
@ConditionalOnProperty(prefix = "app.uploads", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {

    static final Pattern KEY = Pattern
            .compile("^[0-9a-f]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(\\.[a-z0-9]{1,5})?$");
    private static final Pattern EXTENSION = Pattern.compile("^[a-z0-9]{1,5}$");

    private final Path root;

    public LocalFileStorage(UploadProperties properties) throws IOException {
        this.root = Path.of(properties.localPath()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(InputStream content, String extension) throws IOException {
        String suffix = "";
        if (extension != null && !extension.isEmpty()) {
            if (!EXTENSION.matcher(extension).matches()) {
                throw new IllegalArgumentException("Unsupported file extension");
            }
            suffix = "." + extension;
        }
        String id = UUID.randomUUID().toString();
        String key = id.substring(0, 2) + "/" + id + suffix;
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        // Write beside the target and move into place, so a reader never sees a half-written file.
        Path temporary = Files.createTempFile(target.getParent(), "upload-", ".part");
        try {
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new StoredFileMissingException(key);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
