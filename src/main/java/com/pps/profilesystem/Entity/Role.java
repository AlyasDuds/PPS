package com.pps.profilesystem.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Integer roleId;
    
    @Column(name = "role_name", unique = true, nullable = false, length = 50)
    private String roleName;
    
    // Backward compatibility
    public Long getId() { return roleId != null ? roleId.longValue() : null; }
    public void setId(Long id) { this.roleId = id != null ? id.intValue() : null; }
    
    public String getName() { return roleName; }
    public void setName(String name) { this.roleName = name; }
}
