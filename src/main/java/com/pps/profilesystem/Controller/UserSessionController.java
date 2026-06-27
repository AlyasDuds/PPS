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
        List<UserSession> onlineSessions = userSessionRepository.findByIsOnlineTrue();
        List<OnlineUserDTO> onlineUserDTOs = new ArrayList<>();

        for (UserSession session : onlineSessions) {
            try {
                OnlineUserDTO dto = new OnlineUserDTO();
                
                // Safely get user details
                if (session.getUser() != null) {
                    dto.setUserId(session.getUser().getId());
                    dto.setUsername(session.getUser().getUsername() != null ? session.getUser().getUsername() : "N/A");
                    
                    // Get area name if user has areaId
                    if (session.getUser().getAreaId() != null) {
                        try {
                            Area area = areaRepository.findById(session.getUser().getAreaId()).orElse(null);
                            if (area != null && area.getAreaName() != null) {
                                dto.setAreaName(area.getAreaName());
                            } else {
                                dto.setAreaName("N/A");
                            }
                        } catch (Exception e) {
                            dto.setAreaName("N/A");
                        }
                    } else {
                        dto.setAreaName("N/A");
                    }
                } else {
                    dto.setUserId(null);
                    dto.setUsername("Unknown User");
                    dto.setAreaName("N/A");
                }
                
                dto.setIpAddress(session.getIpAddress() != null ? session.getIpAddress() : "N/A");
                
                if (session.getLoginTime() != null) {
                    dto.setLoginTime(session.getLoginTime().format(formatter));
                } else {
                    dto.setLoginTime("N/A");
                }
                
                if (session.getLastActivity() != null) {
                    dto.setLastActivity(session.getLastActivity().format(formatter));
                } else {
                    dto.setLastActivity("N/A");
                }

                // Office name - currently not directly linked to user
                // You may need to add office_id to User entity or create a relationship
                dto.setOfficeName("N/A");

                onlineUserDTOs.add(dto);
            } catch (Exception e) {
                // Skip this session if there's an error, continue with others
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
