package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.Role;
import com.pps.profilesystem.Repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/roles")
public class RoleController {
    
    private final RoleRepository roleRepository;
    
    @GetMapping
    public String listRoles(Model model) {
        List<Role> roles = roleRepository.findAllRolesOrderByName();
        model.addAttribute("roles", roles);
        return "roles/list";
    }
    
    @GetMapping("/api")
    public ResponseEntity<List<Role>> getAllRoles() {
        List<Role> roles = roleRepository.findAllRolesOrderByName();
        return ResponseEntity.ok(roles);
    }
    
    @GetMapping("/api/{id}")
    public ResponseEntity<Role> getRoleById(@PathVariable Integer id) {
        Optional<Role> role = roleRepository.findById(id);
        return role.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/api")
    public ResponseEntity<Role> createRole(@RequestBody Role role) {
        if (roleRepository.existsByRoleName(role.getRoleName())) {
            return ResponseEntity.badRequest().build();
        }
        Role savedRole = roleRepository.save(role);
        return ResponseEntity.ok(savedRole);
    }
    
    @PutMapping("/api/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable Integer id, @RequestBody Role roleDetails) {
        return roleRepository.findById(id)
                .map(role -> {
                    role.setRoleName(roleDetails.getRoleName());
                    return ResponseEntity.ok(roleRepository.save(role));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/api/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable Integer id) {
        return roleRepository.findById(id)
                .map(role -> {
                    roleRepository.delete(role);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("role", new Role());
        return "roles/form";
    }
    
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Integer id, Model model) {
        return roleRepository.findById(id)
                .map(role -> {
                    model.addAttribute("role", role);
                    return "roles/form";
                })
                .orElse("redirect:/roles");
    }
    
    @PostMapping("/save")
    public String saveRole(@ModelAttribute Role role) {
        roleRepository.save(role);
        return "redirect:/roles";
    }
}
