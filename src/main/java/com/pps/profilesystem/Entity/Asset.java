package com.pps.profilesystem.Entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Asset {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(name = "asset_type", nullable = false, length = 50)
    private String assetType;
    
    @Column(name = "serial_number", length = 100)
    private String serialNumber;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
    
    @Column(name = "assigned_to")
    private Long assignedTo;
    
    @Column(name = "location", length = 255)
    private String location;
    
    @Column(name = "purchase_date")
    private java.time.LocalDate purchaseDate;
    
    @Column(name = "purchase_cost")
    private java.math.BigDecimal purchaseCost;
    
    @Column(name = "warranty_expiry")
    private java.time.LocalDate warrantyExpiry;
    
    @Column(name = "is_active")
    private boolean active = true;
    
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}
