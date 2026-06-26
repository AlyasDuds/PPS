package com.pps.profilesystem.Config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter to prevent brute force attacks
 * Limits requests per IP address
 */
@Component
public class RateLimitFilter implements Filter {

    // Store buckets per IP address
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    // Rate limit: 100 requests per minute per IP
    private static final int REQUESTS_PER_MINUTE = 100;
    private static final int REQUESTS_PER_SECOND = 20;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String clientIp = getClientIp(httpRequest);
        
        // Skip rate limiting for static resources
        String path = httpRequest.getRequestURI();
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/") || 
            path.startsWith("/static/") || path.startsWith("/postal-offices/")) {
            chain.doFilter(request, response);
            return;
        }
        
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::createBucket);
        
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
        }
    }
    
    private Bucket createBucket(String ip) {
        // Create a bucket with two refill rates:
        // - 100 requests per minute
        // - 20 requests per second (burst capacity)
        Bandwidth minuteLimit = Bandwidth.builder()
            .capacity(REQUESTS_PER_MINUTE)
            .refillIntervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
            .build();
        Bandwidth secondLimit = Bandwidth.builder()
            .capacity(REQUESTS_PER_SECOND)
            .refillIntervally(REQUESTS_PER_SECOND, Duration.ofSeconds(1))
            .build();
        
        return Bucket.builder()
            .addLimit(minuteLimit)
            .addLimit(secondLimit)
            .build();
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For (take the first one)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
