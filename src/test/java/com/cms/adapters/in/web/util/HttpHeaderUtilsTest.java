package com.cms.adapters.in.web.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpHeaderUtilsTest {

    @Test
    void toEtagShouldWrapValueInQuotes() {
        assertThat(HttpHeaderUtils.toEtag(1L)).isEqualTo("\"1\"");
        assertThat(HttpHeaderUtils.toEtag(42L)).isEqualTo("\"42\"");
    }

    @Test
    void fromEtagShouldParseQuotedLong() {
        assertThat(HttpHeaderUtils.fromEtag("\"1\"")).isEqualTo(1L);
        assertThat(HttpHeaderUtils.fromEtag("\"42\"")).isEqualTo(42L);
    }

    @Test
    void fromEtagShouldRejectNullOrBlank() {
        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ETag cannot be null or empty");

        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromEtagShouldRejectMissingQuotes() {
        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag("1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ETag format");

        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag("\"1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ETag format");

        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag("1\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ETag format");
    }

    @Test
    void fromEtagShouldRejectNonNumericValue() {
        assertThatThrownBy(() -> HttpHeaderUtils.fromEtag("\"abc\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid long");
    }
}

