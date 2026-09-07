package com.cms.domain.model.site;

import com.cms.domain.shared.ContentTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContentTest {

    private static final String ILLEGAL_ARGUMENT_EXCEPTION_MESSAGE =
            "Content cannot be null or blank";

    private static final String CONTENT_TOO_LARGE_EXCEPTION_MESSAGE =
            "Content cannot exceed 1000000 bytes";

    @Test
    void shouldNoThrowExceptionWhenContentIsCreatedCorrectly () {
        assertThatNoException()
                .isThrownBy(() -> new Content("{\"key\": \"value\"}"));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenRawJsonIsNull() {
        assertThatThrownBy(() -> new Content(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ILLEGAL_ARGUMENT_EXCEPTION_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    void shouldThrowIllegalArgumentExceptionWhenRawJsonIsBlank(String rawJson) {
        assertThatThrownBy(() -> new Content(rawJson))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ILLEGAL_ARGUMENT_EXCEPTION_MESSAGE);
    }


    @Test
    void shouldThrowContentTooLargeExceptionWhenRawJsonExceedsMaxLength() {
        assertThatThrownBy(() -> new Content("a".repeat(1_000_001))).isInstanceOf(ContentTooLargeException.class)
                .hasMessage(CONTENT_TOO_LARGE_EXCEPTION_MESSAGE);
    }
}
