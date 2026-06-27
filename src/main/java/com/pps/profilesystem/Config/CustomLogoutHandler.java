package com.pps.profilesystem.Config;

import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Entity.UserSession;
import com.pps.profilesystem.Repository.UserRepository;
import com.pps.profilesystem.Repository.UserSessionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@ConditionalOnBean(UserSessionRepository.class)
public class CustomLogoutHandler implements LogoutHandler {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public CustomLogoutHandler(UserRepository userRepository,
                             UserSessionRepository userSessionRepository) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    public void logout(HttpServletRequest request,
                       HttpServletResponse response,
                       Authentication authentication) {

        if (authentication != null) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null) {
                // Mark user as offline
                UserSession session = userSessionRepository.findByUserAndIsOnlineTrue(user).orElse(null);
                if (session != null) {
                    session.markAsOffline();
                    userSessionRepository.save(session);
                }
            }
        }
    }
}
