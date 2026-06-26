package com.pps.profilesystem.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_online")
    private Boolean isOnline = false;

    @Column(name = "login_time")
    private LocalDateTime loginTime;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    // Constructors
    public UserSession() {}

    public UserSession(User user) {
        this.user = user;
        this.isOnline = true;
        this.loginTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Boolean getIsOnline() { return isOnline; }
    public void setIsOnline(Boolean isOnline) { this.isOnline = isOnline; }

    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }

    public LocalDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }

    public LocalDateTime getLogoutTime() { return logoutTime; }
    public void setLogoutTime(LocalDateTime logoutTime) { this.logoutTime = logoutTime; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    // Helper methods
    public void markAsOnline() {
        this.isOnline = true;
        this.loginTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.logoutTime = null;
    }

    public void markAsOffline() {
        this.isOnline = false;
        this.logoutTime = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
}
