package com.cms.adapters.in.web.util;

public final class HttpHeaderUtils {

    private static final String QUOTE_CHARACTER = "\"";

    private HttpHeaderUtils() {
        throw new AssertionError("Cannot instantiate utility class");
    }

    public static String toEtag(long value) {
        return QUOTE_CHARACTER + value + QUOTE_CHARACTER;
    }

    public static long fromEtag(String etag) {
        if (etag == null || etag.isBlank()) {
            throw new IllegalArgumentException("ETag cannot be null or empty");
        }
        if (etag.length() < 3 || !etag.startsWith(QUOTE_CHARACTER) || !etag.endsWith(QUOTE_CHARACTER)) {
            throw new IllegalArgumentException("Invalid ETag format");
        }
        String value = etag.substring(1, etag.length() - 1);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ETag does not contain a valid long value", e);
        }
    }
}
