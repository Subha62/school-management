package com.schoolmanagement.file;

import com.schoolmanagement.common.exception.FieldValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Decides whether an upload is acceptable, from facts we can check rather than what the client claims: the size,
 * the extension against an allow-list, and the first bytes of the content against that extension. The content
 * type that is stored and later served comes from the allow-list, never from the request.
 */
@Component
public class UploadValidator {

    /** A file that passed validation; the name is display text only and is never used in a path. */
    public record ValidatedUpload(MultipartFile file, String displayName, String extension, String contentType) {
    }

    private record Kind(String contentType, byte[] signature) {
    }

    private static final int MAX_NAME = 150;
    private static final int TEXT_SAMPLE = 8192;
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A,
            (byte) 0xE1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final String OPEN_XML = "application/vnd.openxmlformats-officedocument.";

    /** A text file has no signature, so its content is checked for binary data instead. */
    private static final Map<String, Kind> ALLOWED = Map.ofEntries(
            Map.entry("pdf", new Kind("application/pdf", PDF)),
            Map.entry("png", new Kind("image/png", PNG)),
            Map.entry("jpg", new Kind("image/jpeg", JPEG)),
            Map.entry("jpeg", new Kind("image/jpeg", JPEG)),
            Map.entry("docx", new Kind(OPEN_XML + "wordprocessingml.document", ZIP)),
            Map.entry("xlsx", new Kind(OPEN_XML + "spreadsheetml.sheet", ZIP)),
            Map.entry("pptx", new Kind(OPEN_XML + "presentationml.presentation", ZIP)),
            Map.entry("doc", new Kind("application/msword", OLE)),
            Map.entry("xls", new Kind("application/vnd.ms-excel", OLE)),
            Map.entry("ppt", new Kind("application/vnd.ms-powerpoint", OLE)),
            Map.entry("txt", new Kind("text/plain", null)));

    private final UploadProperties properties;

    public UploadValidator(UploadProperties properties) {
        this.properties = properties;
    }

    public static List<String> allowedExtensions() {
        return ALLOWED.keySet().stream().sorted().toList();
    }

    public ValidatedUpload validate(MultipartFile file) {
        String name = displayName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw rejected(name, "is empty");
        }
        if (file.getSize() > properties.maxFileBytes()) {
            throw rejected(name, "is larger than " + properties.maxFileBytes() / (1024 * 1024) + " MB");
        }
        String extension = extensionOf(name);
        Kind kind = ALLOWED.get(extension);
        if (kind == null) {
            throw rejected(name, "has a type that is not allowed. Allowed: " + String.join(", ", allowedExtensions()));
        }
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(TEXT_SAMPLE);
        } catch (IOException e) {
            throw rejected(name, "could not be read");
        }
        if (kind.signature() == null ? containsNul(header) : !startsWith(header, kind.signature())) {
            throw rejected(name, "does not look like a ." + extension + " file");
        }
        return new ValidatedUpload(file, name, extension, kind.contentType());
    }

    /** The last path segment, without control characters and at most 150 characters (keeping the extension). */
    static String displayName(String submitted) {
        String name = submitted == null ? "" : submitted;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return "file";
        }
        if (name.length() > MAX_NAME) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 6 ? name.substring(dot) : "";
            name = name.substring(0, MAX_NAME - ext.length()) + ext;
        }
        return name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length && Arrays.equals(content, 0, signature.length, signature, 0,
                signature.length);
    }

    private static boolean containsNul(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static FieldValidationException rejected(String name, String reason) {
        return new FieldValidationException("files", "\"" + name + "\" " + reason);
    }
}
