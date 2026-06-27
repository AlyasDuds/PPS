package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.Area;
import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.UserRepository;
import com.pps.profilesystem.Service.QuarterlySnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing quarterly snapshots.
 * Only system admins can create snapshots to freeze historical data.
 */
@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {

    @Autowired
    private QuarterlySnapshotService snapshotService;

    @Autowired
    private com.pps.profilesystem.Repository.QuarterlySnapshotRepository snapshotRepository;

    @Autowired
    private ReportController reportController;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Create a snapshot for a specific year, quarter, and area
     * POST /api/snapshots/create
     * Body: { year: 2026, quarter: "Q1", areaId: null }
     */
    @PostMapping("/create")
    public ResponseEntity<?> createSnapshot(@RequestBody Map<String, Object> body, Authentication auth) {
        // Check if user is system admin
        if (!isSystemAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "message", "Only system admins can create snapshots."
            ));
        }

        Integer year = (Integer) body.get("year");
        String quarter = (String) body.get("quarter");
        Integer areaId = body.get("areaId") != null ? ((Number) body.get("areaId")).intValue() : null;

        if (year == null || quarter == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Year and quarter are required."
            ));
        }

        try {
            // Get current stats for this quarter
            Map<String, Long> stats = reportController.computeConnectivityStats(year, quarter, areaId, null);

            // Create snapshot
            snapshotService.createSnapshot(
                year,
                quarter,
                areaId,
                stats.getOrDefault("totalConnected", 0L),
                stats.getOrDefault("totalNewlyConnected", 0L),
                stats.getOrDefault("totalDisconnected", 0L),
                stats.getOrDefault("totalNewlyDisconnected", 0L),
                stats.getOrDefault("totalOffices", 0L)
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Snapshot created successfully for " + year + " " + quarter + (areaId != null ? " (Area " + areaId + ")" : " (All Areas)")
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Failed to create snapshot: " + e.getMessage()
            ));
        }
    }

    /**
     * Create snapshots for all areas for a specific year and quarter
     * POST /api/snapshots/create-all
     * Body: { year: 2026, quarter: "Q1" }
     */
    @PostMapping("/create-all")
    public ResponseEntity<?> createSnapshotsForAllAreas(@RequestBody Map<String, Object> body, Authentication auth) {
        // Check if user is system admin
        if (!isSystemAdmin(auth)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "message", "Only system admins can create snapshots."
            ));
        }

        Integer year = (Integer) body.get("year");
        String quarter = (String) body.get("quarter");

        if (year == null || quarter == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Year and quarter are required."
            ));
        }

        try {
            // Create snapshot for "All Areas" (areaId = null)
            Map<String, Long> allStats = reportController.computeConnectivityStats(year, quarter, null, null);
            snapshotService.createSnapshot(
                year, quarter, null,
                allStats.getOrDefault("totalConnected", 0L),
                allStats.getOrDefault("totalNewlyConnected", 0L),
                allStats.getOrDefault("totalDisconnected", 0L),
                allStats.getOrDefault("totalNewlyDisconnected", 0L),
                allStats.getOrDefault("totalOffices", 0L)
            );

            // Create snapshots for each individual area
            List<Area> allAreas = areaRepository.findAll();
            int createdCount = 1; // Already created "All Areas"
            
            for (Area area : allAreas) {
                Map<String, Long> areaStats = reportController.computeConnectivityStats(year, quarter, area.getId(), null);
                snapshotService.createSnapshot(
                    year, quarter, area.getId(),
                    areaStats.getOrDefault("totalConnected", 0L),
                    areaStats.getOrDefault("totalNewlyConnected", 0L),
                    areaStats.getOrDefault("totalDisconnected", 0L),
                    areaStats.getOrDefault("totalNewlyDisconnected", 0L),
                    areaStats.getOrDefault("totalOffices", 0L)
                );
                createdCount++;
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Created " + createdCount + " snapshots for " + year + " " + quarter
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Failed to create snapshots: " + e.getMessage()
            ));
        }
    }

    /**
     * Check if a snapshot exists
     * GET /api/snapshots/exists?year=2026&quarter=Q1&areaId=1
     */
    @GetMapping("/exists")
    public ResponseEntity<?> checkSnapshotExists(
            @RequestParam Integer year,
            @RequestParam String quarter,
            @RequestParam(required = false) Integer areaId) {
        boolean exists = snapshotService.hasSnapshot(year, quarter, areaId);
        return ResponseEntity.ok(Map.of(
            "exists", exists,
            "year", year,
            "quarter", quarter,
            "areaId", areaId
        ));
    }

    /**
     * Get current year and quarter info
     * GET /api/snapshots/current-quarter
     */
    @GetMapping("/current-quarter")
    public ResponseEntity<?> getCurrentQuarter() {
        int[] current = snapshotService.getCurrentYearAndQuarter();
        String quarterLabel = "Q" + current[1];
        return ResponseEntity.ok(Map.of(
            "year", current[0],
            "quarter", quarterLabel,
            "quarterNumber", current[1]
        ));
    }

    /**
     * Get all snapshots for a specific year and area
     * GET /api/snapshots/list?year=2026&areaId=1
     */
    @GetMapping("/list")
    public ResponseEntity<?> getSnapshots(
            @RequestParam Integer year,
            @RequestParam(required = false) Integer areaId) {
        try {
            List<com.pps.profilesystem.Entity.QuarterlySnapshot> snapshots;
            if (areaId != null) {
                snapshots = snapshotRepository.findByYearAndAreaId(year, areaId);
            } else {
                snapshots = snapshotRepository.findByYear(year);
            }

            List<Map<String, Object>> result = snapshots.stream()
                .map(s -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", s.getSnapshotId());
                    map.put("year", s.getYear());
                    map.put("quarter", s.getQuarter());
                    map.put("areaId", s.getArea() != null ? s.getArea().getId() : null);
                    map.put("areaName", s.getArea() != null ? s.getArea().getAreaName() : "All Areas");
                    map.put("connectedCount", s.getConnectedCount());
                    map.put("newlyConnectedCount", s.getNewlyConnectedCount());
                    map.put("disconnectedCount", s.getDisconnectedCount());
                    map.put("newlyDisconnectedCount", s.getNewlyDisconnectedCount());
                    map.put("totalOffices", s.getTotalOffices());
                    map.put("createdAt", s.getCreatedAt());
                    return map;
                })
                .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("snapshots", result);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to retrieve snapshots: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    private boolean isSystemAdmin(Authentication auth) {
        if (auth == null) return false;
        String email = auth.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return false;
        Integer roleId = user.getRole();
        return roleId != null && (roleId == 1 || roleId == 4); // System Admin or SRD Operation
    }
}
