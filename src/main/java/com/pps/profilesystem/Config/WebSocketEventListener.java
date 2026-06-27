package com.pps.profilesystem.Config;

import com.pps.profilesystem.Entity.Area;
import com.pps.profilesystem.Entity.PostalOffice;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import com.pps.profilesystem.Repository.UserRepository;
import com.pps.profilesystem.Service.OnlineUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebSocketEventListener {

    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    @EventListener
    public void handleWebSocketConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) return;

        String email = principal.getName();
        userRepository.findByEmail(email).ifPresent(user -> {
            // Get area name
            String areaName = "N/A";
            if (user.getAreaId() != null) {
                Area area = areaRepository.findById(user.getAreaId()).orElse(null);
                if (area != null) {
                    areaName = area.getAreaName();
                }
            }

            // Get office name
            String officeName = "N/A";
            if (user.getPostalOfficeId() != null) {
                PostalOffice office = postalOfficeRepository.findById(user.getPostalOfficeId()).orElse(null);
                if (office != null) {
                    officeName = office.getName();
                }
            }

            onlineUserService.userConnected(
                email,
                user.getUsername(),
                user.getRole(),
                user.getAreaId(),
                areaName,
                officeName
            );
            broadcast();
        });
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) return;

        onlineUserService.userDisconnected(principal.getName());
        broadcast();
    }

    private void broadcast() {
        Map<String, Object> data = new HashMap<>();
        data.put("count", onlineUserService.getOnlineCount());
        data.put("users", onlineUserService.getOnlineUsers());
        messagingTemplate.convertAndSend("/topic/online-users", data, new java.util.HashMap<>());
    }
}
