package com.cms.adapters.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.CacheControlHeadersWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.XContentTypeOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersTest {

    private HeaderWriterFilter buildFilter(boolean hstsEnabled) {
        var writers = new java.util.ArrayList<>(List.of(
                new XContentTypeOptionsHeaderWriter(),
                new XFrameOptionsHeaderWriter(XFrameOptionsMode.DENY),
                new CacheControlHeadersWriter(),
                new StaticHeadersWriter("Referrer-Policy", "no-referrer")
        ));
        if (hstsEnabled) {
            writers.add(new org.springframework.security.web.header.writers.HstsHeaderWriter(
                    31536000, true
            ));
        }
        return new HeaderWriterFilter(writers);
    }

    @Test
    void xContentTypeOptionsIsNoSniff() throws Exception {
        MockHttpServletResponse response = doRequest();
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void xFrameOptionsIsDeny() throws Exception {
        MockHttpServletResponse response = doRequest();
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void cacheControlIsNoStore() throws Exception {
        MockHttpServletResponse response = doRequest();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }

    @Test
    void referrerPolicyIsNoReferrer() throws Exception {
        MockHttpServletResponse response = doRequest();
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @Test
    void hstsAbsentWhenDisabled() throws Exception {
        MockHttpServletResponse response = doRequest(false);
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void hstsPresentWhenEnabled() throws Exception {
        // HstsHeaderWriter only writes on HTTPS requests
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cms/sites");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        buildFilter(true).doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("Strict-Transport-Security")).isNotNull();
    }

    private MockHttpServletResponse doRequest() throws Exception {
        return doRequest(false);
    }

    private MockHttpServletResponse doRequest(boolean hstsEnabled) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cms/sites");
        MockHttpServletResponse response = new MockHttpServletResponse();
        buildFilter(hstsEnabled).doFilter(request, response, new MockFilterChain());
        return response;
    }
}
