package com.schoolmanagement.auth.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef1!gh", "Correct-Horse-9-Battery", "Zz9#zzzzzzzz", "Pässwörd1!xyz"})
    void acceptsPasswordsMeetingEveryRule(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ab1!", "abcdefgh1!", "ABCDEFGH1!", "Abcdefghi!", "Abcdefghi1", "          "})
    void rejectsPasswordsMissingAClassOrTooShort(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsPasswordsLongerThanTheBcryptLimitInBytes() {
        String tooLong = "Aa1!" + "x".repeat(69);
        String atLimit = "Aa1!" + "x".repeat(68);

        assertThat(validator.isValid(tooLong, null)).isFalse();
        assertThat(validator.isValid(atLimit, null)).isTrue();
    }

    @Test
    void multiByteCharactersCountTowardsTheByteLimit() {
        String password = "Aa1!" + "é".repeat(35);

        assertThat(password.length()).isLessThan(72);
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void leavesNullToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
