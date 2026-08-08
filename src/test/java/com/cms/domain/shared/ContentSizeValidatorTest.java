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
    void shouldPassForContentJustBelowLimit() {
        // JSON serialization adds overhead (quotes, escaping, braces, key names),
        // so we use less than MAX_BYTES for the raw value to stay within 1MB after serialization.
        // This test verifies that the validator correctly counts serialized bytes, not raw string length.
        Map<String, Object> content = new HashMap<>();
        content.put("body", "x".repeat(ContentSizeValidator.MAX_BYTES - 100));

        assertThatNoException().isThrownBy(() -> ContentSizeValidator.validate(content));
    }

    @Test
    void shouldPassForEmptyContent() {
        assertThatNoException().isThrownBy(() -> ContentSizeValidator.validate(Map.of()));
    }

    @Test
    void shouldThrowIllegalArgumentForSerializationError() {
        // Circular reference causes Jackson to fail serialization.
        // This should throw IllegalArgumentException (server-side issue), not ContentTooLargeException.
        Map<String, Object> content = new HashMap<>();
        content.put("self", content); // Circular reference

        assertThatThrownBy(() -> ContentSizeValidator.validate(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server-side issue");
    }
}
