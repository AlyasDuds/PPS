package com.pps.profilesystem.Config;

import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Entity.UserSession;
import com.pps.profilesystem.Repository.UserRepository;
import com.pps.profilesystem.Repository.UserSessionRepository;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.session.tracking.enabled", havingValue = "true", matchIfMissing = false)
public class SessionEventListener implements HttpSessionListener {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        try {
            // Get authentication from security context (may still be available during session destruction)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                User user = userRepository.findByUsername(username).orElse(null);

                if (user != null) {
                    // Mark user as offline on session timeout
                    UserSession session = userSessionRepository.findByUserAndIsOnlineTrue(user).orElse(null);
                    if (session != null) {
                        session.markAsOffline();
                        userSessionRepository.save(session);
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the application startup
            System.err.println("Error in session destroyed event: " + e.getMessage());
        }
    }
}
