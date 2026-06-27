package com.pps.profilesystem.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    @Autowired(required = false)
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Autowired(required = false)
    private CustomLogoutHandler customLogoutHandler;

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // Run before Spring Security filter
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new HybridPasswordEncoder();
    }

    @Bean
    public AccessDeniedHandler customAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            // Log the specific URL and user that caused the denial
            System.err.println("Access Denied to: " + request.getRequestURI() + 
                             " by user: " + request.getUserPrincipal() + 
                             " Reason: " + accessDeniedException.getMessage());
            
            // Check if response is already committed
            if (response.isCommitted()) {
                System.err.println("Response already committed - cannot redirect");
                return;
            }

            // SSE requests may have already started streaming via OutputStream.
            // Writing a JSON body (getWriter) would throw IllegalStateException.
            String accept = request.getHeader("Accept");
            if (accept != null && accept.contains("text/event-stream")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            
            // For AJAX/SSE requests, return 403 JSON
            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")) ||
                request.getRequestURI().startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                try {
                    response.getWriter().write("{\"error\":\"Access denied\",\"status\":403}");
                    response.flushBuffer();
                } catch (Exception e) {
                    System.err.println("Error writing JSON response: " + e.getMessage());
                }
            } else {
                // For regular requests, redirect to access denied page
                try {
                    response.sendRedirect("/access-denied");
                } catch (Exception e) {
                    System.err.println("Error redirecting: " + e.getMessage());
                }
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(new CookieCsrfTokenRepository())
                .ignoringRequestMatchers("/login", "/error", "/error/**", "/access-denied")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login",
                    "/error",
                    "/error/**",
                    "/access-denied",
                    "/request-otp",
                    "/verify-otp",
                    "/reset-password",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/static/assets/**",
                    "/postal-offices/**",
                    "/ws/**",                   // WebSocket endpoint
                    "/api/keep-alive",          // public ping for session check
                    "/api/user/current"         // public check for authentication status
                ).permitAll()
                // Only ADMIN, AREA_ADMIN, and SRD_OPERATION can access user management and archive
                // AREA_ADMIN can access but sees only their own area's data
                .requestMatchers("/users", "/register").hasAnyRole("ADMIN", "AREA_ADMIN", "SRD_OPERATION")
                .requestMatchers("/archive", "/api/archive/**", "/api/restore/**").hasAnyRole("ADMIN", "AREA_ADMIN", "SRD_OPERATION")
                // Only AREA_ADMIN and SRD_OPERATION can access approval system
                .requestMatchers("/approvals/**").hasAnyRole("AREA_ADMIN", "SRD_OPERATION")
                // All authenticated users can receive approval/connectivity notifications.
                .requestMatchers("/api/notifications/**").authenticated()
                // Asset login/page restriction
                // Inventory: ADMIN/ASSET can edit, others can view only
                .requestMatchers(HttpMethod.GET, "/inventory", "/inventory/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/inventory", "/inventory/**").hasAnyRole("ADMIN", "ASSET")
                .requestMatchers(HttpMethod.PUT, "/inventory", "/inventory/**").hasAnyRole("ADMIN", "ASSET")
                .requestMatchers(HttpMethod.DELETE, "/inventory", "/inventory/**").hasAnyRole("ADMIN", "ASSET")
                // Assets: ADMIN/ASSET can edit, others can view table only
                .requestMatchers(HttpMethod.GET, "/assets", "/assets/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/assets", "/assets/**").hasAnyRole("ADMIN", "ASSET")
                .requestMatchers(HttpMethod.PUT, "/assets", "/assets/**").hasAnyRole("ADMIN", "ASSET")
                .requestMatchers(HttpMethod.DELETE, "/assets", "/assets/**").hasAnyRole("ADMIN", "ASSET")
                // Asset profile details: ADMIN/ASSET only
                .requestMatchers(HttpMethod.GET, "/assets/profile", "/assets/profile/**").hasAnyRole("ADMIN", "ASSET")
                .requestMatchers(HttpMethod.GET, "/assets/dashboard", "/assets/dashboard/**").hasAnyRole("ADMIN", "ASSET")
                .requestMatchers(HttpMethod.GET, "/assets/age", "/assets/age/**").hasAnyRole("ADMIN", "ASSET")
                .anyRequest().authenticated()
            )
            .formLogin(form -> {
                form.loginPage("/login")
                    .loginProcessingUrl("/login")
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .failureUrl("/login?error=true")
                    .defaultSuccessUrl("/dashboard", true)
                    .permitAll();
                // Only add custom success handler if it exists
                if (customAuthenticationSuccessHandler != null) {
                    form.successHandler(customAuthenticationSuccessHandler);
                }
            })
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession()
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .expiredUrl("/login?expired=true")
            )
            .logout(logout -> {
                logout.logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
                    .permitAll();
                // Only add custom logout handler if it exists
                if (customLogoutHandler != null) {
                    logout.addLogoutHandler(customLogoutHandler);
                }
            })
            .exceptionHandling(exception -> exception
                .accessDeniedHandler(customAccessDeniedHandler())
            )
            // Configure frame options for profile popup - allow same origin
            // Also add cache control headers so authenticated pages are never cached.
            // This prevents the browser Back button from showing protected pages after logout.
            // Content Security Policy (CSP) to mitigate XSS and data injection attacks
            .headers(headers -> {
                headers.frameOptions(frameOptions -> frameOptions.sameOrigin());
                headers.xssProtection(xss -> xss.disable());
                headers.contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net https://code.jquery.com https://cdn.datatables.net https://cdnjs.cloudflare.com; " +
                        "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://unpkg.com https://cdn.datatables.net; " +
                        "img-src 'self' data: https://ui-avatars.com https://*.tile.openstreetmap.org; " +
                        "font-src 'self' https://unpkg.com https://cdnjs.cloudflare.com; " +
                        "connect-src 'self' https://cdn.jsdelivr.net https://unpkg.com; " +
                        "frame-src 'self'; " +
                        "object-src 'none'; " +
                        "base-uri 'self'; " +
                        "form-action 'self';"
                    )
                );
                headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                );
                headers.referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicy.SAME_ORIGIN)
                );
                headers.addHeaderWriter(new org.springframework.security.web.header.writers.PermissionsPolicyHeaderWriter(
                    "geolocation=(), microphone=(), camera=()"
                ));
                headers.cacheControl(cache -> {});
                headers.addHeaderWriter(new org.springframework.security.web.header.writers.XContentTypeOptionsHeaderWriter());
                headers.addHeaderWriter(new org.springframework.security.web.header.writers.XXssProtectionHeaderWriter());
            });

        return http.build();
    }
}