package com.cms.adapters.in.web.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    void shouldDelegateToFilterChain() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/cms/sites");

        filter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldNotAlterResponseStatus() throws Exception {
        response.setStatus(201);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
    }

    @Test
    void shouldCompleteEvenWhenFilterChainThrows() throws Exception {
        MockFilterChain throwingChain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throw new jakarta.servlet.ServletException("simulated error");
            }
        };

        org.junit.jupiter.api.Assertions.assertThrows(
                jakarta.servlet.ServletException.class,
                () -> filter.doFilterInternal(request, response, throwingChain)
        );
    }
}
