package com.pps.profilesystem.Config;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                      HttpServletResponse response,
                                      Authentication authentication) throws IOException, ServletException {
        
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
