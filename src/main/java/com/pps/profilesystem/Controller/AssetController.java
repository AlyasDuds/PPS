package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.Asset;
import com.pps.profilesystem.Repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/assets")
public class AssetController {
    
    private final AssetRepository assetRepository;
    
    @GetMapping
    public String listAssets(Model model) {
        List<Asset> assets = assetRepository.findActiveAssetsOrderByName();
        model.addAttribute("assets", assets);
        return "assets/list";
    }
    
    @GetMapping("/api")
    public ResponseEntity<List<Asset>> getAllAssets() {
        List<Asset> assets = assetRepository.findActiveAssetsOrderByName();
        return ResponseEntity.ok(assets);
    }
    
    @GetMapping("/api/{id}")
    public ResponseEntity<Asset> getAssetById(@PathVariable Long id) {
        Optional<Asset> asset = assetRepository.findById(id);
        return asset.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/api/user/{userId}")
    public ResponseEntity<List<Asset>> getAssetsByUser(@PathVariable Long userId) {
        List<Asset> assets = assetRepository.findActiveAssetsByUser(userId);
        return ResponseEntity.ok(assets);
    }
    
    @GetMapping("/api/type/{type}")
    public ResponseEntity<List<Asset>> getAssetsByType(@PathVariable String type) {
        List<Asset> assets = assetRepository.findByAssetTypeAndStatus(type, "ACTIVE");
        return ResponseEntity.ok(assets);
    }
    
    @GetMapping("/api/warranty/expiring")
    public ResponseEntity<List<Asset>> getAssetsWithWarrantyExpiring(
            @RequestParam(defaultValue = "30") int days) {
        LocalDate date = LocalDate.now().plusDays(days);
        List<Asset> assets = assetRepository.findAssetsWithWarrantyExpiringBefore(date);
        return ResponseEntity.ok(assets);
    }
    
    @PostMapping("/api")
    public ResponseEntity<Asset> createAsset(@RequestBody Asset asset) {
        if (asset.getSerialNumber() != null && 
            assetRepository.existsBySerialNumber(asset.getSerialNumber())) {
            return ResponseEntity.badRequest().build();
        }
        asset.setActive(true);
        if (asset.getStatus() == null) {
            asset.setStatus("ACTIVE");
        }
        Asset savedAsset = assetRepository.save(asset);
        return ResponseEntity.ok(savedAsset);
    }
    
    @PutMapping("/api/{id}")
    public ResponseEntity<Asset> updateAsset(@PathVariable Long id, @RequestBody Asset assetDetails) {
        return assetRepository.findById(id)
                .map(asset -> {
                    asset.setName(assetDetails.getName());
                    asset.setAssetType(assetDetails.getAssetType());
                    asset.setSerialNumber(assetDetails.getSerialNumber());
                    asset.setDescription(assetDetails.getDescription());
                    asset.setStatus(assetDetails.getStatus());
                    asset.setAssignedTo(assetDetails.getAssignedTo());
                    asset.setLocation(assetDetails.getLocation());
                    asset.setPurchaseDate(assetDetails.getPurchaseDate());
                    asset.setPurchaseCost(assetDetails.getPurchaseCost());
                    asset.setWarrantyExpiry(assetDetails.getWarrantyExpiry());
                    asset.setActive(assetDetails.isActive());
                    return ResponseEntity.ok(assetRepository.save(asset));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/api/{id}")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        return assetRepository.findById(id)
                .map(asset -> {
                    asset.setActive(false);
                    assetRepository.save(asset);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("asset", new Asset());
        return "assets/form";
    }
    
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        return assetRepository.findById(id)
                .map(asset -> {
                    model.addAttribute("asset", asset);
                    return "assets/form";
                })
                .orElse("redirect:/assets");
    }
    
    @PostMapping("/save")
    public String saveAsset(@ModelAttribute Asset asset) {
        assetRepository.save(asset);
        return "redirect:/assets";
    }
    
    @PostMapping("/api/{id}/assign")
    public ResponseEntity<Asset> assignAsset(@PathVariable Long id, @RequestParam Long userId) {
        return assetRepository.findById(id)
                .map(asset -> {
                    asset.setAssignedTo(userId);
                    return ResponseEntity.ok(assetRepository.save(asset));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/api/stats")
    public ResponseEntity<AssetStats> getAssetStats() {
        long totalAssets = assetRepository.countActiveAssets();
        BigDecimal totalValue = assetRepository.getTotalValueOfActiveAssets();
        
        return ResponseEntity.ok(new AssetStats(totalAssets, totalValue));
    }
    
    public record AssetStats(long totalCount, BigDecimal totalValue) {}
}
