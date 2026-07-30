package com.cms.domain.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class ContentSizeValidator {

    static final int MAX_BYTES = 1024 * 1024; // 1MB

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentSizeValidator() {}

    public static void validate(Map<String, Object> content) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(content);
            if (bytes.length > MAX_BYTES) {
                throw new ContentTooLargeException(
                        "Content size %d bytes exceeds the 1MB limit".formatted(bytes.length)
                );
            }
        } catch (JsonProcessingException e) {
            throw new ContentTooLargeException("Content could not be serialized: " + e.getMessage());
        }
    }
}
