package com.cms.domain.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Validates that a content map (JSONB) does not exceed the 1MB size limit.
 * Uses JSON serialization size as the metric to match actual storage size.
 *
 * Should be called in use cases before persisting draft content.
 */
@Slf4j
public final class ContentSizeValidator {

    static final int MAX_BYTES = 1024 * 1024; // 1MB

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentSizeValidator() {
        throw new AssertionError("Utility class - cannot be instantiated");
    }

    /**
     * Validates that the given content does not exceed 1MB when serialized to JSON.
     *
     * @param content the content map to validate
     * @throws ContentTooLargeException if serialized size > 1MB
     * @throws IllegalArgumentException if content cannot be serialized (indicates a bug,
     *                                  not a user-facing payload-too-large issue)
     */
    public static void validate(Map<String, Object> content) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(content);
            if (bytes.length > MAX_BYTES) {
                throw new ContentTooLargeException(
                        "Content size %d bytes exceeds the 1MB limit".formatted(bytes.length)
                );
            }
        } catch (JsonProcessingException e) {
            log.error("Content serialization failed (likely a programming error)", e);
            throw new IllegalArgumentException(
                    "Content could not be serialized. This indicates a server-side issue, not a payload-too-large condition.",
                    e
            );
        }
    }
}
