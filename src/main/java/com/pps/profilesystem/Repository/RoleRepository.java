package com.pps.profilesystem.Repository;

import com.pps.profilesystem.Entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    
    Optional<Role> findByRoleName(String roleName);
    
    List<Role> findByRoleNameContainingIgnoreCase(String roleName);
    
    @Query("SELECT r FROM Role r ORDER BY r.roleName")
    List<Role> findAllRolesOrderByName();
    
    boolean existsByRoleName(String roleName);
}
