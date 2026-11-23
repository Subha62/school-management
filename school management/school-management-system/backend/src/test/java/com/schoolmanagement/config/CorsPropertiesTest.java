package com.schoolmanagement.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void rejectsWildcardOriginsBecauseCredentialsAreAllowed() {
        assertThatThrownBy(() -> new CorsProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CorsProperties(List.of("https://*.example.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsExplicitOrigins() {
        assertThat(new CorsProperties(List.of("https://school.example")).allowedOrigins())
                .containsExactly("https://school.example");
    }
}
