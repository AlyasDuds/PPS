package com.pps.profilesystem.Controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * StorageController
 * 
 * Provides storage monitoring functionality to check disk usage and capacity.
 * Only accessible by administrators.
 */
@Controller
public class StorageController {

    @GetMapping("/api/storage/status")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public Map<String, Object> getStorageStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get upload directory path
            String uploadDir = "uploads";
            Path uploadPath = Paths.get(uploadDir);
            
            if (!Files.exists(uploadPath)) {
                response.put("success", false);
                response.put("message", "Upload directory not found");
                return response;
            }
            
            // Calculate total size of upload directory
            long totalSize = calculateDirectorySize(uploadPath);
            
            // Get disk space information
            File diskPartition = new File("/");
            long totalDiskSpace = diskPartition.getTotalSpace();
            long freeDiskSpace = diskPartition.getFreeSpace();
            long usedDiskSpace = totalDiskSpace - freeDiskSpace;
            
            // Calculate percentages
            double diskUsagePercent = (double) usedDiskSpace / totalDiskSpace * 100;
            double uploadDirSizeMB = totalSize / (1024.0 * 1024.0);
            double totalDiskSpaceGB = totalDiskSpace / (1024.0 * 1024.0 * 1024.0);
            double usedDiskSpaceGB = usedDiskSpace / (1024.0 * 1024.0 * 1024.0);
            double freeDiskSpaceGB = freeDiskSpace / (1024.0 * 1024.0 * 1024.0);
            
            // Determine status
            String status = "healthy";
            if (diskUsagePercent > 90) {
                status = "critical";
            } else if (diskUsagePercent > 80) {
                status = "warning";
            }
            
            // Build response
            response.put("success", true);
            response.put("status", status);
            response.put("diskUsage", Map.of(
                "totalGB", String.format("%.2f", totalDiskSpaceGB),
                "usedGB", String.format("%.2f", usedDiskSpaceGB),
                "freeGB", String.format("%.2f", freeDiskSpaceGB),
                "usagePercent", String.format("%.1f", diskUsagePercent)
            ));
            response.put("uploads", Map.of(
                "sizeMB", String.format("%.2f", uploadDirSizeMB),
                "path", uploadDir
            ));
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error calculating storage: " + e.getMessage());
        }
        
        return response;
    }
    
    private long calculateDirectorySize(Path directory) {
        try {
            return Files.walk(directory)
                .filter(path -> Files.isRegularFile(path))
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
        } catch (Exception e) {
            return 0;
        }
    }
}
