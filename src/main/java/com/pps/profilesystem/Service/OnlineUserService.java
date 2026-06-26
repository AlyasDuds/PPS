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
        Map<String, Object> info = new ConcurrentHashMap<>();
        info.put("email", email);
        info.put("username", username);
        info.put("roleId", roleId);
        info.put("areaId", areaId);
        info.put("areaName", areaName != null ? areaName : "N/A");
        info.put("officeName", officeName != null ? officeName : "N/A");
        info.put("connectedAt", LocalDateTime.now().format(formatter));
        onlineUsers.put(email, info);
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
