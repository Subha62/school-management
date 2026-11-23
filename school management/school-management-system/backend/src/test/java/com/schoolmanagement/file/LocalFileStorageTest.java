package com.schoolmanagement.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalFileStorageTest {

    @TempDir
    Path root;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        storage = new LocalFileStorage(new UploadProperties("local", root.toString(), 1024 * 1024, 10, 5));
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storesUnderAGeneratedKeyAndReadsItBack() throws IOException {
        String key = storage.store(bytes("hello"), "pdf");

        assertThat(key).matches(LocalFileStorage.KEY).endsWith(".pdf");
        assertThat(Files.readString(root.resolve(key))).isEqualTo("hello");
        try (var in = storage.open(key)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void everyStoreGetsADifferentKeyAndLeavesNoTemporaryFile() throws IOException {
        String first = storage.store(bytes("a"), "txt");
        String second = storage.store(bytes("a"), "txt");

        assertThat(first).isNotEqualTo(second);
        try (Stream<Path> all = Files.walk(root)) {
            assertThat(all.filter(Files::isRegularFile).map(p -> p.getFileName().toString()))
                    .noneMatch(name -> name.endsWith(".part"));
        }
    }

    @Test
    void deletingTwiceIsNotAnError() throws IOException {
        String key = storage.store(bytes("x"), "txt");

        storage.delete(key);
        storage.delete(key);

        assertThat(Files.exists(root.resolve(key))).isFalse();
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(StoredFileMissingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.txt", "ab/../../outside.txt", "/etc/passwd", "ab/cd.txt",
            "..\\outside.txt", "", "ab/12345678-1234-1234-1234-123456789012/../x",
            "AB/12345678-1234-1234-1234-123456789012.txt", "ab/12345678-1234-1234-1234-123456789012.tar.gz",
            "ab/12345678-1234-1234-1234-123456789012.txt\u0000.png"})
    void keysThatAreNotOurOwnAreRefusedBeforeTheDiskIsTouched(String key) {
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", "a/b", "PDF", "toolongext", "p df"})
    void extensionsAreRestrictedToShortLowerCaseWords(String extension) {
        assertThatThrownBy(() -> storage.store(bytes("x"), extension))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingFileIsReportedAsMissing() {
        assertThatThrownBy(() -> storage.open("ab/12345678-1234-1234-1234-123456789012.txt"))
                .isInstanceOf(StoredFileMissingException.class);
    }
}
