package com.pps.profilesystem.Service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlineUserService {

    // email → user info
    private final Map<String, Map<String, Object>> onlineUsers = 
        new ConcurrentHashMap<>();

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    public void userConnected(String email, String username, Integer roleId, Integer areaId, String areaName, String officeName) {
        // Add null guard to prevent NullPointerException in ConcurrentHashMap
        if (email == null) {
            System.out.println("⚠️ Warning: Cannot track online user. Email is null!");
            return;
        }

        Map<String, Object> info = new ConcurrentHashMap<>();
        info.put("email", email);
        info.put("username", username != null ? username : "N/A");
        info.put("roleId", roleId != null ? roleId : 0);
        info.put("areaId", areaId != null ? areaId : 0);
        info.put("areaName", areaName != null ? areaName : "N/A");
        info.put("officeName", officeName != null ? officeName : "N/A");
        info.put("connectedAt", LocalDateTime.now().format(formatter));
        
        try {
            onlineUsers.put(email, info);
        } catch (NullPointerException e) {
            System.out.println("⚠️ Warning: Cannot track online user. Null value detected in info map.");
            e.printStackTrace();
        }
    }

    public void userDisconnected(String email) {
        onlineUsers.remove(email);
    }

    public Collection<Map<String, Object>> getOnlineUsers() {
        return onlineUsers.values();
    }

    public int getOnlineCount() {
        return onlineUsers.size();
    }
}
