package com.cms.domain.model.site;

import com.cms.domain.shared.ContentTooLargeException;

import java.nio.charset.StandardCharsets;

public record Content(String rawJson) {

    public static Content empty(){
        return new Content("{}");
    }

    public Content {
        long MAX_CONTENT_ALLOWED_LENGTH = 1_000_000L; // 1 MB

        if( rawJson == null || rawJson.isBlank() ) {
            throw new IllegalArgumentException("Content cannot be null or blank");
        }

        if( rawJson.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_ALLOWED_LENGTH) {
            throw new ContentTooLargeException("Content cannot exceed " + MAX_CONTENT_ALLOWED_LENGTH + " bytes");
        }
    }

}
