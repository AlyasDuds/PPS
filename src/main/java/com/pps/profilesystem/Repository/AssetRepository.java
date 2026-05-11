package com.pps.profilesystem.Repository;

import com.pps.profilesystem.Entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    
    List<Asset> findByAssetType(String assetType);
    
    List<Asset> findByStatus(String status);
    
    List<Asset> findByAssignedTo(Long assignedTo);
    
    List<Asset> findByActive(boolean active);
    
    Optional<Asset> findBySerialNumber(String serialNumber);
    
    List<Asset> findByNameContainingIgnoreCase(String name);
    
    List<Asset> findByAssetTypeAndStatus(String assetType, String status);
    
    @Query("SELECT a FROM Asset a WHERE a.active = true ORDER BY a.name")
    List<Asset> findActiveAssetsOrderByName();
    
    @Query("SELECT a FROM Asset a WHERE a.assignedTo = :userId AND a.active = true")
    List<Asset> findActiveAssetsByUser(@Param("userId") Long userId);
    
    @Query("SELECT a FROM Asset a WHERE a.warrantyExpiry <= :date AND a.active = true")
    List<Asset> findAssetsWithWarrantyExpiringBefore(@Param("date") LocalDate date);
    
    @Query("SELECT COUNT(a) FROM Asset a WHERE a.active = true")
    long countActiveAssets();
    
    @Query("SELECT COUNT(a) FROM Asset a WHERE a.assetType = :type AND a.active = true")
    long countActiveAssetsByType(@Param("type") String type);
    
    @Query("SELECT SUM(a.purchaseCost) FROM Asset a WHERE a.active = true")
    BigDecimal getTotalValueOfActiveAssets();
    
    boolean existsBySerialNumber(String serialNumber);
}
