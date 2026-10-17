package com.khedma.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khedma.common.ErrorCode;
import com.khedma.common.ErrorResponse;
import com.khedma.config.AppProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Caps how often one client IP can hit the public sign-in endpoints (password guessing, email spam). */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final AppProperties.RateLimit settings;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(RateLimiter limiter, AppProperties properties, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.settings = properties.rateLimit();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !settings.enabled() || !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!limiter.tryAcquire("auth-ip:" + clientIp(request), settings.authRequestsPerIp(), settings.authWindow())) {
            ErrorCode code = ErrorCode.RATE_LIMITED;
            response.setStatus(code.status().value());
            response.setHeader("Retry-After", String.valueOf(settings.authWindow().toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code));
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Render sits behind Cloudflare, which sets CF-Connecting-IP itself; X-Forwarded-For's first entry is
     * the fallback. These can be spoofed when the origin is reached directly, which is why accounts also
     * have their own per-email limits that don't depend on the IP.
     */
    static String clientIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
