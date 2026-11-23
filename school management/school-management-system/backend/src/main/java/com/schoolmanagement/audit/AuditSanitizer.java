package com.schoolmanagement.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Makes audit values safe to store and to show: anything that looks like a credential is replaced, text is stripped
 * of control characters and shortened, and the size of the structure is bounded. The sanitizer works on a copy and
 * never fails: an unexpected value is stored as its (shortened) text.
 */
public final class AuditSanitizer {

    public static final String REDACTED = "[REDACTED]";
    static final int MAX_TEXT = 500;
    static final int MAX_KEYS = 40;
    static final int MAX_ITEMS = 20;
    static final int MAX_DEPTH = 3;
    private static final Pattern SECRET_KEY = Pattern.compile(
            "(?i).*(pass(word|wd|phrase)?|secret|token|hash|authorization|cookie|credential|api.?key|private.?key|"
                    + "signing|otp|pin).*");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}]");

    private AuditSanitizer() {
    }

    /** Free text for the summary column. */
    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = CONTROL.matcher(value).replaceAll(" ").trim();
        return cleaned.length() > MAX_TEXT ? cleaned.substring(0, MAX_TEXT) : cleaned;
    }

    public static Map<String, Object> values(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        return map(input, 0);
    }

    private static Map<String, Object> map(Map<?, ?> input, int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (count++ >= MAX_KEYS) {
                out.put("…", "more fields omitted");
                break;
            }
            String key = text(String.valueOf(entry.getKey()));
            out.put(key, SECRET_KEY.matcher(key).matches() ? REDACTED : value(entry.getValue(), depth + 1));
        }
        return out;
    }

    private static Object value(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> nested) {
            return depth > MAX_DEPTH ? "[nested values omitted]" : map(nested, depth);
        }
        if (value instanceof Iterable<?> items) {
            if (depth > MAX_DEPTH) {
                return "[nested values omitted]";
            }
            List<Object> list = new ArrayList<>();
            for (Object item : items) {
                if (list.size() >= MAX_ITEMS) {
                    list.add("more items omitted");
                    break;
                }
                list.add(value(item, depth + 1));
            }
            return list;
        }
        return text(String.valueOf(value));
    }
}
