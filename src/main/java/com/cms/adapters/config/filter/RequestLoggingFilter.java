package com.cms.adapters.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(2)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        int status = 500;

        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} {} {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    durationMs);
        }
    }
}
