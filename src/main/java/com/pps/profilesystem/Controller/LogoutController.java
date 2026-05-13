package com.pps.profilesystem.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LogoutController {

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response,
                         @RequestParam(value = "timeout", required = false) Boolean timeout) {
        
        // Get authentication object
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null) {
            // Log the logout activity
            String username = auth.getName();
            System.out.println("User " + username + " is logging out" + 
                             (timeout != null && timeout ? " due to session timeout" : ""));
            
            // Perform logout
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        
        // Clear any remaining session data
        request.getSession().invalidate();
        
        // Redirect to login with appropriate message
        if (timeout != null && timeout) {
            return "redirect:/login?timeout=true";
        } else {
            return "redirect:/login?logout=true";
        }
    }
    
    @PostMapping("/logout")
    public String logoutPost(HttpServletRequest request, HttpServletResponse response) {
        return logout(request, response, false);
    }
}
