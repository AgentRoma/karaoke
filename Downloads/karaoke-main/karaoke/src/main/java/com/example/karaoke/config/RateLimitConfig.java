package com.example.karaoke.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prosty rate limiter oparty na IP (in-memory).
 * W produkcji zalecane: Redis + Bucket4j / Resilience4j.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    // Konfiguracja limitów (zwiększone dla wygody - prod: Tuning w env)
    private static final int SEARCH_REQUESTS_PER_MINUTE = 300;
    private static final int QUEUE_REQUESTS_PER_MINUTE = 300;
    private static final int GENERAL_REQUESTS_PER_MINUTE = 300;

    // Store: IP -> (windowStart, count)
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ip = getClientIp(request);
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Pomijaj rate limiting dla GET (odczyt) oraz dla admina z naglowkiem X-Admin-Auth
        // Odswiezenie kolejki/szukanie nie moze blokowac Zamow/Graj/Usun
        boolean isAdminRequest = "true".equals(request.getHeader("X-Admin-Auth"));

        if ("GET".equals(method) || isAdminRequest) {
            filterChain.doFilter(request, response);
            return;
        }

        // Okresl limit na podstawie sciezki (tylko dla POST/PUT/DELETE)
        int limit;
        if (path.startsWith("/api/songs")) {
            limit = SEARCH_REQUESTS_PER_MINUTE;
        } else if (path.startsWith("/api/queue")) {
            limit = QUEUE_REQUESTS_PER_MINUTE;
        } else {
            limit = GENERAL_REQUESTS_PER_MINUTE;
        }

        RateLimitBucket bucket = buckets.computeIfAbsent(ip, k -> new RateLimitBucket());
        boolean allowed = bucket.tryConsume(limit);

        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please slow down.\"}");
            response.setHeader("Retry-After", "60");
            return;
        }

        // Dodaj naglowki rate limit
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getRemaining(limit)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(bucket.getResetTime()));

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        // Render (i inne proksi) wysyla X-Forwarded-For jako liste: "client, proxy1, proxy2"
        // Bierzemy pierwszy (prawdziwy klient) - pomijamy "unknown" i puste wartosci
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            String[] ips = xf.split(",");
            for (String ip : ips) {
                String candidate = ip.trim();
                if (!candidate.isEmpty() && !"unknown".equalsIgnoreCase(candidate)) {
                    return candidate;
                }
            }
        }
        String xr = request.getHeader("X-Real-IP");
        if (xr != null && !xr.isEmpty() && !"unknown".equalsIgnoreCase(xr.trim())) {
            return xr.trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateLimitBucket {
        private volatile long windowStart = Instant.now().getEpochSecond();
        private final AtomicInteger count = new AtomicInteger(0);

        boolean tryConsume(int limit) {
            long now = Instant.now().getEpochSecond();
            if (now - windowStart >= 60) {
                // Nowe okno czasowe
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }

        int getRemaining(int limit) {
            return Math.max(0, limit - count.get());
        }

        long getResetTime() {
            return windowStart + 60;
        }
    }
}