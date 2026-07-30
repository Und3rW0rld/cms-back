package com.cms.adapters.config;

import com.cms.adapters.in.web.constant.ApiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig(
            List.of("http://localhost:3000", "http://localhost:5173")
    );

    @Test
    void shouldAllowConfiguredOrigins() {
        UrlBasedCorsConfigurationSource source = buildSource();
        CorsConfiguration config = source.getCorsConfiguration(requestFrom("http://localhost:3000"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowedOrigins()).contains("http://localhost:3000", "http://localhost:5173");
    }

    @Test
    void shouldAllowRequiredMethods() {
        UrlBasedCorsConfigurationSource source = buildSource();
        CorsConfiguration config = source.getCorsConfiguration(requestFrom("http://localhost:3000"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    void shouldAllowRequiredHeaders() {
        UrlBasedCorsConfigurationSource source = buildSource();
        CorsConfiguration config = source.getCorsConfiguration(requestFrom("http://localhost:3000"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowedHeaders())
                .contains(
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.IF_MATCH,
                        ApiHeaders.CORRELATION_ID
                );
    }

    @Test
    void shouldExposeRequiredHeaders() {
        UrlBasedCorsConfigurationSource source = buildSource();
        CorsConfiguration config = source.getCorsConfiguration(requestFrom("http://localhost:3000"));

        assertThat(config).isNotNull();
        assertThat(config.getExposedHeaders())
                .contains(HttpHeaders.ETAG, ApiHeaders.CORRELATION_ID);
    }

    @Test
    void shouldHaveMaxAgeOf3600() {
        UrlBasedCorsConfigurationSource source = buildSource();
        CorsConfiguration config = source.getCorsConfiguration(requestFrom("http://localhost:3000"));

        assertThat(config).isNotNull();
        assertThat(config.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void shouldNotAllowCredentials() {
        UrlBasedCorsConfigurationSource source = buildSource();
        CorsConfiguration config = source.getCorsConfiguration(requestFrom("http://localhost:3000"));

        assertThat(config).isNotNull();
        assertThat(config.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    // --- helpers ---

    private UrlBasedCorsConfigurationSource buildSource() {
        CapturingCorsRegistry registry = new CapturingCorsRegistry();
        corsConfig.addCorsMappings(registry);
        return registry.toSource();
    }

    private MockHttpServletRequest requestFrom(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/cms/sites");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        return request;
    }

    static class CapturingCorsRegistry extends CorsRegistry {
        UrlBasedCorsConfigurationSource toSource() {
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            getCorsConfigurations().forEach(source::registerCorsConfiguration);
            return source;
        }
    }
}
