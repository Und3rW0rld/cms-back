package com.cms.adapters.in.web.validation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonValidatorTest {

    private final JsonValidator validator = new JsonValidator(new ObjectMapper());

    @Test
    void shouldAllowNullOrBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("   ", null)).isTrue();
    }

    @Test
    void shouldAcceptJsonObjectOnly() {
        assertThat(validator.isValid("{}", null)).isTrue();
        assertThat(validator.isValid("{\"title\":\"My Portfolio\"}", null)).isTrue();
    }

    @Test
    void shouldRejectNonObjectJson() {
        assertThat(validator.isValid("[]", null)).isFalse();
        assertThat(validator.isValid("\"str\"", null)).isFalse();
        assertThat(validator.isValid("123", null)).isFalse();
        assertThat(validator.isValid("true", null)).isFalse();
        assertThat(validator.isValid("null", null)).isFalse();
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThat(validator.isValid("{", null)).isFalse();
        assertThat(validator.isValid("{\"a\":", null)).isFalse();
    }
}

