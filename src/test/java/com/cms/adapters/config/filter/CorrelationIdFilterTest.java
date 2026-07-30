package com.cms.adapters.config.filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldUseCorrelationIdFromRequestHeader() throws Exception {
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "existing-id");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isEqualTo("existing-id");
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderAbsent() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isNotBlank();
    }

    @Test
    void shouldGenerateCorrelationIdWhenHeaderIsBlank() throws Exception {
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("   ");
    }

    @Test
    void shouldGenerateDifferentIdsForEachRequest() throws Exception {
        filter.doFilterInternal(request, response, chain);
        String firstId = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilterInternal(new MockHttpServletRequest(), secondResponse, new MockFilterChain());
        String secondId = secondResponse.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    void shouldClearMdcAfterRequestCompletes() throws Exception {
        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }

    @Test
    void shouldClearMdcEvenWhenFilterChainThrows() throws Exception {
        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throw new jakarta.servlet.ServletException("simulated error");
            }
        };

        try {
            filter.doFilterInternal(request, response, throwingChain);
        } catch (jakarta.servlet.ServletException ignored) {}

        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    }
}
