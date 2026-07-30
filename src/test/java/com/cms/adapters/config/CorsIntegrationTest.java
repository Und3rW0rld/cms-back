package com.cms.adapters.config;

import com.cms.adapters.in.web.constant.ApiHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsProcessor;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsIntegrationTest {

    private CorsFilter corsFilter;

    @BeforeEach
    void setUp() {
        CorsConfig corsConfig = new CorsConfig(
                List.of("http://localhost:3000", "http://localhost:5173")
        );
        CapturingCorsRegistry registry = new CapturingCorsRegistry();
        corsConfig.addCorsMappings(registry);
        corsFilter = new CorsFilter(registry.toSource());
    }

    @Test
    void preflightFromAllowedOriginReturns200() throws Exception {
        MockHttpServletRequest request = preflight("http://localhost:3000", HttpMethod.POST);
        MockHttpServletResponse response = new MockHttpServletResponse();

        corsFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:3000");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .isNotBlank();
    }

    @Test
    void preflightFromUnlistedOriginIsRejected() throws Exception {
        MockHttpServletRequest request = preflight("https://evil.com", HttpMethod.POST);
        MockHttpServletResponse response = new MockHttpServletResponse();

        corsFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }

    @Test
    void correlationIdHeaderIsExposed() throws Exception {
        MockHttpServletRequest request = preflight("http://localhost:3000", HttpMethod.GET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        corsFilter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains(ApiHeaders.CORRELATION_ID);
    }

    // --- helpers ---

    private MockHttpServletRequest preflight(String origin, HttpMethod method) {
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/cms/sites");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method.name());
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
