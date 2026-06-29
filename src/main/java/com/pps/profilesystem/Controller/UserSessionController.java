package com.pps.profilesystem.Controller;

import com.pps.profilesystem.DTO.OnlineUserDTO;
import com.pps.profilesystem.Entity.Area;
import com.pps.profilesystem.Entity.UserSession;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.UserSessionRepository;
import com.pps.profilesystem.Service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-sessions")
public class UserSessionController {

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private OnlineUserService onlineUserService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    @GetMapping("/online-count")
    public ResponseEntity<Map<String, Object>> getOnlineCount() {
        List<UserSession> onlineSessions = userSessionRepository.findByIsOnlineTrue();
        long onlineCount = onlineSessions.size();

        Map<String, Object> response = new HashMap<>();
        response.put("onlineCount", onlineCount);
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/online-users")
    public ResponseEntity<Map<String, Object>> getOnlineUsers() {
        // Use OnlineUserService instead of UserSessionRepository to match WebSocket data
        Collection<Map<String, Object>> onlineUsers = onlineUserService.getOnlineUsers();
        List<OnlineUserDTO> onlineUserDTOs = new ArrayList<>();

        for (Map<String, Object> userInfo : onlineUsers) {
            try {
                OnlineUserDTO dto = new OnlineUserDTO();
                
                dto.setUsername(userInfo.get("username") != null ? userInfo.get("username").toString() : "N/A");
                dto.setAreaName(userInfo.get("areaName") != null ? userInfo.get("areaName").toString() : "N/A");
                dto.setOfficeName(userInfo.get("officeName") != null ? userInfo.get("officeName").toString() : "N/A");
                dto.setLoginTime(userInfo.get("connectedAt") != null ? userInfo.get("connectedAt").toString() : "N/A");
                dto.setIpAddress("N/A");
                dto.setLastActivity("N/A");

                onlineUserDTOs.add(dto);
            } catch (Exception e) {
                // Skip this user if there's an error, continue with others
                continue;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("onlineUsers", onlineUserDTOs);
        response.put("onlineCount", onlineUserDTOs.size());
        response.put("success", true);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/online-users-socket")
    public ResponseEntity<Map<String, Object>> getOnlineUsersSocket() {
        Map<String, Object> response = new HashMap<>();
        response.put("count", onlineUserService.getOnlineCount());
        response.put("users", onlineUserService.getOnlineUsers());
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}
