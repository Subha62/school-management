package com.schoolmanagement.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditSanitizerTest {

    @Test
    void credentialsAreRedactedWhateverTheirCase() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("password", "hunter2");
        input.put("newPassword", "x");
        input.put("refresh_token", "abc");
        input.put("Authorization", "Bearer abc");
        input.put("jwtSigningSecret", "s");
        input.put("passwordHash", "$2a$..");
        input.put("apiKey", "k");
        input.put("name", "Tuition");

        Map<String, Object> out = AuditSanitizer.values(input);

        assertThat(out.get("name")).isEqualTo("Tuition");
        assertThat(out).containsEntry("password", AuditSanitizer.REDACTED).containsEntry("newPassword",
                AuditSanitizer.REDACTED).containsEntry("refresh_token", AuditSanitizer.REDACTED)
                .containsEntry("Authorization", AuditSanitizer.REDACTED).containsEntry("jwtSigningSecret",
                        AuditSanitizer.REDACTED).containsEntry("passwordHash", AuditSanitizer.REDACTED)
                .containsEntry("apiKey", AuditSanitizer.REDACTED);
        assertThat(out.toString()).doesNotContain("hunter2").doesNotContain("Bearer");
    }

    @Test
    void nestedValuesAreRedactedAndBounded() {
        Map<String, Object> input = Map.of("user", Map.of("email", "a@b.c", "credentials", Map.of("pw", "x")),
                "items", List.of(Map.of("token", "t"), "plain"));

        Map<String, Object> out = AuditSanitizer.values(input);

        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) out.get("user");
        assertThat(user.get("email")).isEqualTo("a@b.c");
        assertThat(user.get("credentials")).isEqualTo(AuditSanitizer.REDACTED);
        assertThat(out.get("items").toString()).contains(AuditSanitizer.REDACTED).doesNotContain("t}");

        Map<String, Object> deep = Map.of("a", Map.of("b", Map.of("c", Map.of("d", Map.of("e", "too deep")))));
        assertThat(AuditSanitizer.values(deep).toString()).doesNotContain("too deep");

        Map<String, Object> many = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            many.put("k" + i, i);
        }
        assertThat(AuditSanitizer.values(many)).hasSizeLessThanOrEqualTo(AuditSanitizer.MAX_KEYS + 1);
    }

    @Test
    void textLosesControlCharactersAndIsShortened() {
        assertThat(AuditSanitizer.text("line1\nline2\u0000\u202E end")).doesNotContain("\n").doesNotContain("\u0000")
                .doesNotContain("\u202E");
        assertThat(AuditSanitizer.text("x".repeat(2000))).hasSize(AuditSanitizer.MAX_TEXT);
        assertThat(AuditSanitizer.text(null)).isNull();
        assertThat(AuditSanitizer.values(null)).isNull();
        assertThat(AuditSanitizer.values(Map.of())).isNull();
        assertThat(AuditSanitizer.values(Map.of("note", "a\r\nb")).get("note").toString()).doesNotContain("\n");
    }
}
