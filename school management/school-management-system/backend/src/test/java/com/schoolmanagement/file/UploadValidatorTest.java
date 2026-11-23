package com.schoolmanagement.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.schoolmanagement.common.exception.FieldValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class UploadValidatorTest {

    private static final long LIMIT = 1024 * 1024;

    private final UploadValidator validator = new UploadValidator(new UploadProperties("local", "unused", LIMIT, 10, 5));

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private static byte[] withHeader(byte[] header, int total) {
        byte[] content = new byte[total];
        System.arraycopy(header, 0, content, 0, header.length);
        return content;
    }

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1};

    @Test
    void acceptsEveryAllowedKindAndStoresTheTypeFromTheAllowList() {
        assertThat(validator.validate(file("a.pdf", withHeader(PDF, 20))).contentType()).isEqualTo("application/pdf");
        assertThat(validator.validate(file("a.png", withHeader(PNG, 20))).contentType()).isEqualTo("image/png");
        assertThat(validator.validate(file("a.JPG", withHeader(JPEG, 20))).contentType()).isEqualTo("image/jpeg");
        assertThat(validator.validate(file("a.docx", withHeader(ZIP, 20))).contentType()).contains("wordprocessingml");
        assertThat(validator.validate(file("a.xlsx", withHeader(ZIP, 20))).contentType()).contains("spreadsheetml");
        assertThat(validator.validate(file("a.pptx", withHeader(ZIP, 20))).contentType()).contains("presentationml");
        assertThat(validator.validate(file("a.doc", withHeader(OLE, 20))).contentType()).isEqualTo("application/msword");
        assertThat(validator.validate(file("a.txt", "plain text".getBytes(StandardCharsets.UTF_8))).contentType())
                .isEqualTo("text/plain");
    }

    @ParameterizedTest
    @CsvSource({"malware.exe", "page.html", "image.svg", "script.js", "archive.zip", "noextension", "trailingdot.",
            "double.pdf.exe"})
    void refusesTypesThatAreNotOnTheList(String name) {
        assertThatThrownBy(() -> validator.validate(file(name, "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void refusesContentThatDoesNotMatchTheExtension() {
        // A script renamed to .pdf, a PNG renamed to .jpg, an Office file renamed to .pdf.
        assertThatThrownBy(() -> validator.validate(file("a.pdf", "<script>alert(1)</script>".getBytes())))
                .isInstanceOf(FieldValidationException.class).hasMessageContaining("One or more");
        assertThatThrownBy(() -> validator.validate(file("a.jpg", withHeader(PNG, 20))))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> validator.validate(file("a.pdf", withHeader(ZIP, 20))))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void refusesBinaryDataPresentedAsText() {
        assertThatThrownBy(() -> validator.validate(file("notes.txt", new byte[] {'o', 'k', 0, 1, 2})))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void refusesEmptyAndTooLargeFiles() {
        assertThatThrownBy(() -> validator.validate(file("a.pdf", new byte[0])))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> validator.validate(file("a.pdf", withHeader(PDF, (int) LIMIT + 1))))
                .isInstanceOf(FieldValidationException.class);
        assertThat(validator.validate(file("a.pdf", withHeader(PDF, (int) LIMIT))).displayName()).isEqualTo("a.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd.txt", "C:\\Users\\me\\notes.txt", "dir/sub/notes.txt"})
    void keepsOnlyTheLastPathSegmentAsDisplayText(String submitted) {
        assertThat(UploadValidator.displayName(submitted)).isEqualTo(submitted.endsWith("passwd.txt") ? "passwd.txt"
                : "notes.txt");
    }

    @Test
    void cleansUpDisplayNames() {
        assertThat(UploadValidator.displayName(null)).isEqualTo("file");
        assertThat(UploadValidator.displayName("..")).isEqualTo("file");
        assertThat(UploadValidator.displayName("bad\u0000na\nme.pdf")).isEqualTo("badname.pdf");
        assertThat(UploadValidator.displayName("report\u202Efdp.exe")).isEqualTo("reportfdp.exe");
        String longName = "x".repeat(300) + ".pdf";
        assertThat(UploadValidator.displayName(longName)).hasSize(150).endsWith(".pdf");
    }
}
