package com.schoolmanagement.common.persistence;

import org.springframework.data.jpa.domain.Specification;

/** Helpers for building search specifications safely. */
public final class SearchSpecs {

    private static final char ESCAPE = '\\';

    private SearchSpecs() {
    }

    /** A case-insensitive "contains" match on one attribute; blank search text matches everything. */
    public static <T> Specification<T> containsIgnoreCase(String attribute, String search) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = "%" + escape(search.trim().toLowerCase()) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(attribute)), pattern, ESCAPE);
    }

    public static <T> Specification<T> equalTo(String attribute, Object value) {
        if (value == null) {
            return Specification.where(null);
        }
        return (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    /** LIKE pattern matching any of several attributes, for free-text search boxes. */
    public static <T> Specification<T> anyContains(String search, String... attributes) {
        if (search == null || search.isBlank()) {
            return Specification.where(null);
        }
        String pattern = "%" + escape(search.trim().toLowerCase()) + "%";
        return (root, query, cb) -> {
            var predicates = new jakarta.persistence.criteria.Predicate[attributes.length];
            for (int i = 0; i < attributes.length; i++) {
                predicates[i] = cb.like(cb.lower(root.get(attributes[i])), pattern, ESCAPE);
            }
            return cb.or(predicates);
        };
    }

    public static String likePattern(String search) {
        return "%" + escape(search == null ? "" : search.trim().toLowerCase()) + "%";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
