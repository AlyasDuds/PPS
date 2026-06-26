package com.pps.profilesystem.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlineUserDTO {
    private Long userId;
    private String username;
    private String officeName;
    private String areaName;
    private String loginTime;
    private String lastActivity;
    private String ipAddress;
}
