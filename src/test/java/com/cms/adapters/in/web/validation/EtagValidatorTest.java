package com.cms.adapters.in.web.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EtagValidatorTest {

    private final EtagValidator validator = new EtagValidator();

    @Test
    void shouldAcceptQuotedDigitsOnly() {
        assertThat(validator.isValid("\"1\"", null)).isTrue();
        assertThat(validator.isValid("\"123\"", null)).isTrue();
    }

    @Test
    void shouldRejectNullEmptyOrInvalidFormats() {
        assertThat(validator.isValid(null, null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid("1", null)).isFalse();
        assertThat(validator.isValid("\"1", null)).isFalse();
        assertThat(validator.isValid("1\"", null)).isFalse();
        assertThat(validator.isValid("\"abc\"", null)).isFalse();
        assertThat(validator.isValid("\"1\"\"", null)).isFalse();
        assertThat(validator.isValid("\"-1\"", null)).isFalse();
    }
}

