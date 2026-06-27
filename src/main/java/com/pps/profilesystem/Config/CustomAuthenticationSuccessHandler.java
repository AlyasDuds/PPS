package com.pps.profilesystem.Config;

import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Entity.UserSession;
import com.pps.profilesystem.Repository.UserRepository;
import com.pps.profilesystem.Repository.UserSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@ConditionalOnBean(UserSessionRepository.class)
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public CustomAuthenticationSuccessHandler(UserRepository userRepository,
                                               UserSessionRepository userSessionRepository) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {

        try {
            // Get current user
            String username = authentication.getName();
            System.out.println("Login attempt for username: " + username);
            
            User user = userRepository.findByUsername(username).orElse(null);
            
            if (user == null) {
                System.out.println("User not found by username, trying email lookup...");
                user = userRepository.findByEmail(username).orElse(null);
            }

            if (user != null) {
                System.out.println("User found: " + user.getUsername() + " (ID: " + user.getId() + ")");
                // Create or update user session
                UserSession session = userSessionRepository.findByUserAndIsOnlineTrue(user).orElse(null);
                if (session == null) {
                    System.out.println("Creating new session for user: " + user.getUsername());
                    session = new UserSession(user);
                    session.setIpAddress(request.getRemoteAddr());
                    session.setUserAgent(request.getHeader("User-Agent"));
                    userSessionRepository.save(session);
                    System.out.println("Session created successfully");
                } else {
                    System.out.println("Updating existing session for user: " + user.getUsername());
                    session.updateActivity();
                    userSessionRepository.save(session);
                    System.out.println("Session updated successfully");
                }
            } else {
                System.out.println("ERROR: User not found for username/email: " + username);
            }
        } catch (Exception e) {
            // Log error but don't block login - user session tracking is optional
            System.err.println("Error tracking user session: " + e.getMessage());
            e.printStackTrace();
        }

        // Check if user has ASSET role
        boolean isAssetUser = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ASSET"));

        if (isAssetUser) {
            // Redirect asset users to inventory page
            response.sendRedirect("/inventory");
        } else {
            // Redirect other users to default dashboard
            response.sendRedirect("/dashboard");
        }
    }
}
