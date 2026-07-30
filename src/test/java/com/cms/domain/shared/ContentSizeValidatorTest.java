package com.cms.domain.shared;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentSizeValidatorTest {

    @Test
    void shouldPassForSmallContent() {
        Map<String, Object> content = Map.of("title", "Hello", "body", "Short content");

        assertThatNoException().isThrownBy(() -> ContentSizeValidator.validate(content));
    }

    @Test
    void shouldThrowForContentExceedingOneMb() {
        Map<String, Object> content = new HashMap<>();
        content.put("body", "x".repeat(ContentSizeValidator.MAX_BYTES + 1));

        assertThatThrownBy(() -> ContentSizeValidator.validate(content))
                .isInstanceOf(ContentTooLargeException.class)
                .hasMessageContaining("exceeds the 1MB limit");
    }

    @Test
    void shouldPassForContentExactlyAtLimit() {
        // A map with content just under 1MB — the overhead of JSON serialization
        // means we use slightly less than MAX_BYTES for the value
        Map<String, Object> content = new HashMap<>();
        content.put("body", "x".repeat(ContentSizeValidator.MAX_BYTES - 100));

        assertThatNoException().isThrownBy(() -> ContentSizeValidator.validate(content));
    }

    @Test
    void shouldPassForEmptyContent() {
        assertThatNoException().isThrownBy(() -> ContentSizeValidator.validate(Map.of()));
    }
}
