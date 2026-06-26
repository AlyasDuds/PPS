package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.Area;
import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Repository.ArchivedOfficeRepository;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.ConnectivityRepository;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import com.pps.profilesystem.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.cache.annotation.Cacheable;
import java.util.*;

@Controller
@RequestMapping("/quarters")
public class QuartersController {

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    @Autowired
    private ConnectivityRepository connectivityRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private ArchivedOfficeRepository archivedOfficeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReportController reportController;

    // Match ReportController: ignore these offices for 'newly connected' counts
    private static final java.util.Set<Integer> NEWLY_CONNECTED_IGNORE = java.util.Set.of(1364, 1365, 1366, 1374);

    @GetMapping
    public String showQuartersPage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String areaFilter,
            @RequestParam(required = false) String quarterFilter,
            @RequestParam(required = false) String statusFilter,
            Model model) {

        // Get the logged-in user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Integer roleId = currentUser != null ? currentUser.getRole() : null;
        Integer userAreaId = currentUser != null ? currentUser.getAreaId() : null;

        int currentYear = (year != null) ? year : LocalDate.now().getYear();

        // Set default quarter to current quarter if none specified
        String currentQuarter = getCurrentQuarterInfo().get("quarter").toString();
        String selectedQuarter = (quarterFilter != null && !quarterFilter.isEmpty())
                ? quarterFilter : currentQuarter;

        // Parse areaId from filter string
        Integer areaId = null;
        if (areaFilter != null && !areaFilter.trim().isEmpty()) {
            try {
                areaId = Integer.parseInt(areaFilter.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        // Apply user area restrictions: non-system-admin users can only see their assigned area
        if (roleId != null && roleId != 1 && roleId != 4) {
            // User is not a system admin, restrict to their assigned area
            if (userAreaId != null) {
                // If no area filter is set, default to user's area
                if (areaId == null) {
                    areaId = userAreaId;
                } else if (!areaId.equals(userAreaId)) {
                    // User is trying to access an area they're not assigned to - redirect to their area
                    areaId = userAreaId;
                }
            } else {
                // User has no area assigned, show no data
                areaId = -1; // Invalid area ID that will return no results
            }
        }
        // If roleId is null or roleId == 1 (system admin), allow all areas

        model.addAttribute("currentYear", currentYear);
        model.addAttribute("currentQuarterInfo", getCurrentQuarterInfo());
        model.addAttribute("areas", getAreas(roleId, userAreaId));

        // Pass filter-aware stats (same logic as Connectivity Report)
        model.addAttribute("connectivityStats",
                reportController.computeConnectivityStats(currentYear, selectedQuarter, areaId, statusFilter));

        model.addAttribute("selectedAreaFilter", areaId != null && areaId != -1 ? areaId.toString() : "");
        model.addAttribute("selectedQuarterFilter", selectedQuarter);
        model.addAttribute("selectedStatusFilter", statusFilter);
        model.addAttribute("activePage", "quarters");

        Map<String, Boolean> userAccess = new HashMap<>();
        userAccess.put("can_access_all_areas", roleId != null && (roleId == 1 || roleId == 4));
        model.addAttribute("userAccess", userAccess);
        model.addAttribute("isSystemAdmin", roleId != null && (roleId == 1 || roleId == 4));
        model.addAttribute("isAreaAdmin", roleId != null && roleId == 2);
        model.addAttribute("isAnyAdmin", roleId != null && (roleId == 1 || roleId == 2));
        model.addAttribute("userAreaId", userAreaId);

        boolean isSrdOperation = roleId != null && roleId == 4;
        model.addAttribute("isSrdOperation", isSrdOperation);
        // Quarters table actions: all roles except SRD Operation may request edits (USER goes through approval in API).
        model.addAttribute("canQuarterEdit", roleId != null && !isSrdOperation);
        // Matches SecurityConfig: /api/archive/** allows ADMIN, AREA_ADMIN, SRD_OPERATION only (not ROLE_USER).
        model.addAttribute("canQuarterArchive", roleId != null && (roleId == 1 || roleId == 2 || roleId == 4));

        return "quarters";
    }

    // ── Stats Cards (Active / Inactive / Total) ───────────────────────────────

    @Cacheable(value = "connectivityStats", key = "#year + '_' + #quarterFilter + '_' + (#areaId == null ? 'null' : #areaId) + '_' + (#statusFilter == null ? 'null' : #statusFilter)")
private Map<String, Long> getConnectivityStats(
            int year, String quarterFilter, Integer areaId, String statusFilter) {

        // 1. If areaId is null (All Areas), build by summing all individual areas (1 to 9)
        if (areaId == null) {
            List<Area> allAreas = areaRepository.findAll();
            Map<String, Long> stats = new HashMap<>();
            long totalConnected = 0;
            long totalDisconnected = 0;
            long totalOffices = 0;
            for (Area area : allAreas) {
                Map<String, Long> areaStats = getConnectivityStats(year, quarterFilter, area.getId(), statusFilter);
                totalConnected += areaStats.getOrDefault("totalConnected", 0L);
                totalDisconnected += areaStats.getOrDefault("totalDisconnected", 0L);
                totalOffices += areaStats.getOrDefault("totalOffices", 0L);
            }
            stats.put("totalConnected", totalConnected);
            stats.put("totalDisconnected", totalDisconnected);
            stats.put("totalOffices", totalOffices);
            return stats;
        }

        // 2. Carry forward currentYear Q4 for year > currentYear
        int currentYear = LocalDate.now().getYear();
        if (year > currentYear) {
            Map<String, Long> stats = new HashMap<>();
            Map<String, Long> lastQ = getConnectivityStats(currentYear, "Q4", areaId, null);
            long baseTotal = lastQ.getOrDefault("totalOffices", 0L);
            long baseDisconnected = lastQ.getOrDefault("totalDisconnected", 0L);
            long baseConnected = baseTotal - baseDisconnected;
            if (baseConnected < 0) baseConnected = 0;
            
            if ("active".equals(statusFilter)) {
                stats.put("totalConnected", baseConnected);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices", baseConnected);
            } else if ("inactive".equals(statusFilter)) {
                stats.put("totalConnected", 0L);
                stats.put("totalDisconnected", baseDisconnected);
                stats.put("totalOffices", baseDisconnected);
            } else if ("newly_connected".equals(statusFilter) || "newly_disconnected".equals(statusFilter)) {
                stats.put("totalConnected", 0L);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices", 0L);
            } else {
                stats.put("totalConnected", baseConnected);
                stats.put("totalDisconnected", baseDisconnected);
                stats.put("totalOffices", baseTotal);
            }
            return stats;
        }

        Map<String, Long> stats = new HashMap<>();
        try {
            // Resolve snapshot date based on quarter filter
            LocalDateTime snapshotDate = resolveSnapshotDate(year, quarterFilter);

            // Count active offices at snapshot date (filter-aware)
            long active = countActiveAt(snapshotDate, areaId);
            long total = countTotal(areaId);
            // Disconnected = total offices minus connected offices
            // This correctly counts offices with no connectivity record as disconnected
            long inactive = total - active;
            if (inactive < 0) inactive = 0;

            // Apply status filter to what we show
            if ("active".equals(statusFilter)) {
                stats.put("totalConnected", active);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices", active);
            } else if ("inactive".equals(statusFilter)) {
                stats.put("totalConnected", 0L);
                stats.put("totalDisconnected", inactive);
                stats.put("totalOffices", inactive);
            } else if ("newly_connected".equals(statusFilter)) {
                LocalDateTime[] qRange = resolveQuarterRange(year, quarterFilter);
                long newlyConnected = countNewlyConnected(qRange[0], qRange[1], areaId);
                stats.put("totalConnected", newlyConnected);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices", newlyConnected);
            } else if ("newly_disconnected".equals(statusFilter)) {
                LocalDateTime[] qRange = resolveQuarterRange(year, quarterFilter);
                long newlyDisconnected = countNewlyDisconnected(qRange[0], qRange[1], areaId);
                stats.put("totalConnected", 0L);
                stats.put("totalDisconnected", newlyDisconnected);
                stats.put("totalOffices", newlyDisconnected);
            } else {
                // All Status: show active + inactive, total = all non-archived offices
                stats.put("totalConnected", active);
                stats.put("totalDisconnected", inactive);
                stats.put("totalOffices", total);

        }
        } catch (Exception e) {
            stats.put("totalConnected", 0L);
            stats.put("totalDisconnected", 0L);
            stats.put("totalOffices", 0L);
        }
        return stats;
    }



    // ── Helper: resolve quarter start and end dates ───────────────────────────

    private LocalDateTime[] resolveQuarterRange(int year, String quarterFilter) {
        String q = (quarterFilter == null || quarterFilter.isEmpty()) ? "Q1" : quarterFilter.toUpperCase();
        switch (q) {
            case "Q1":
                return new LocalDateTime[]{
                        LocalDateTime.of(year, 1, 1, 0, 0, 0),
                        LocalDateTime.of(year, 3, 31, 23, 59, 59)};
            case "Q2":
                return new LocalDateTime[]{
                        LocalDateTime.of(year, 4, 1, 0, 0, 0),
                        LocalDateTime.of(year, 6, 30, 23, 59, 59)};
            case "Q3":
                return new LocalDateTime[]{
                        LocalDateTime.of(year, 7, 1, 0, 0, 0),
                        LocalDateTime.of(year, 9, 30, 23, 59, 59)};
            default:
                return new LocalDateTime[]{
                        LocalDateTime.of(year, 10, 1, 0, 0, 0),
                        LocalDateTime.of(year, 12, 31, 23, 59, 59)};
        }
    }

    // ── Helper: snapshot date based on quarter filter ─────────────────────────

    private LocalDateTime resolveSnapshotDate(int year, String quarterFilter) {
        // Default to current quarter snapshot
        if (quarterFilter == null || quarterFilter.isEmpty()) {
            quarterFilter = getCurrentQuarterInfo().get("quarter").toString();
        }
        // For the current year + current quarter, snapshot at NOW (in-progress).
        if (isCurrentQuarter(year, quarterFilter)) {
            return LocalDateTime.now();
        }
        switch (quarterFilter.toUpperCase()) {
            case "Q1":
                return LocalDateTime.of(year, 3, 31, 23, 59, 59);
            case "Q2":
                return LocalDateTime.of(year, 6, 30, 23, 59, 59);
            case "Q3":
                return LocalDateTime.of(year, 9, 30, 23, 59, 59);
            case "Q4":
                return LocalDateTime.of(year, 12, 31, 23, 59, 59);
            default:
                return LocalDateTime.of(year, 12, 31, 23, 59, 59);
        }
    }

    private boolean isCurrentQuarter(int year, String quarterFilter) {
        if (quarterFilter == null || quarterFilter.isEmpty()) return false;
        int currentYear = LocalDate.now().getYear();
        if (year != currentYear) return false;
        String currentQuarter = getCurrentQuarterInfo().get("quarter").toString();
        return quarterFilter.equalsIgnoreCase(currentQuarter);
    }

    // ── Helper: count active offices = offices with connectionStatus=true ────────

    private long countActiveAt(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        
        // Use current connection status from PostalOffice as the source of truth,
        // which matches how the QuartersApiController filters the data table.
        if (areaId == null) {
            return postalOfficeRepository.countNonArchivedByConnectionStatus(true);
        }
        
        return postalOfficeRepository.findByIsArchivedFalse().stream()
                .filter(po -> Boolean.TRUE.equals(po.getConnectionStatus()))
                .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
                .count();
    }



    // ── Helper: count total non-archived offices, optionally by area ──────────

    private long countTotal(Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        if (areaId == null) {
            return postalOfficeRepository.countNonArchived();
        }
        return postalOfficeRepository.findByIsArchivedFalse().stream()
                .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
                .count();
    }

    // ── Helper: count newly connected in date range, optionally by area ───────

    private long countNewlyConnected(LocalDateTime start, LocalDateTime end, Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        java.util.List<com.pps.profilesystem.Entity.Connectivity> candidates = connectivityRepository.findByDateConnectedBetween(start, end);
        java.util.Set<Integer> newly = new java.util.HashSet<>();
        for (com.pps.profilesystem.Entity.Connectivity c : candidates) {
            if (c.getPostalOffice() == null) continue;
            Integer oid = c.getPostalOffice().getId();
            if (archivedOfficeRepository.existsByPostalOfficeId(oid)) continue;
            if (NEWLY_CONNECTED_IGNORE.contains(oid)) continue;
            if (areaId != null && (c.getPostalOffice().getArea() == null || !areaId.equals(c.getPostalOffice().getArea().getId()))) continue;

            boolean hasEarlier = connectivityRepository.findByPostalOfficeId(oid).stream()
                .map(cc -> cc.getDateConnected() != null ? cc.getDateConnected() : cc.getCreatedStamp())
                .filter(Objects::nonNull)
                .anyMatch(dt -> dt.isBefore(start));

            if (!hasEarlier) newly.add(oid);
        }
        return newly.size();
    }

    // ── Helper: count newly disconnected in date range, optionally by area ────

    private long countNewlyDisconnected(LocalDateTime start, LocalDateTime end, Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        return connectivityRepository.findByDateDisconnectedBetween(start, end).stream()
                .filter(c -> c.getPostalOffice() != null
                        && !archivedOfficeRepository.existsByPostalOfficeId(c.getPostalOffice().getId()))
                .filter(c -> areaId == null
                        || (c.getPostalOffice().getArea() != null
                        && areaId.equals(c.getPostalOffice().getArea().getId())))
                .map(c -> c.getPostalOffice().getId())
                .distinct()
                .count();
    }

    // ── Other helpers ─────────────────────────────────────────────────────────

    private Map<String, Object> getCurrentQuarterInfo() {
        Map<String, Object> info = new HashMap<>();
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        String quarter;
        long daysUntilNext;
        if (month <= 3) {
            quarter = "Q1";
            daysUntilNext = LocalDate.of(now.getYear(), 4, 1).toEpochDay() - now.toEpochDay();
        } else if (month <= 6) {
            quarter = "Q2";
            daysUntilNext = LocalDate.of(now.getYear(), 7, 1).toEpochDay() - now.toEpochDay();
        } else if (month <= 9) {
            quarter = "Q3";
            daysUntilNext = LocalDate.of(now.getYear(), 10, 1).toEpochDay() - now.toEpochDay();
        } else {
            quarter = "Q4";
            daysUntilNext = LocalDate.of(now.getYear() + 1, 1, 1).toEpochDay() - now.toEpochDay();
        }
        info.put("quarter", quarter);
        info.put("monthName", now.getMonth().toString());
        info.put("daysUntilNext", daysUntilNext);
        return info;
    }

    private List<Area> getAreas(Integer roleId, Integer userAreaId) {
        try {
            List<Area> all = areaRepository.findAll();
            if (roleId != null && (roleId == 1 || roleId == 4)) return all;
            if (userAreaId == null) return new ArrayList<>();
            return all.stream()
                    .filter(a -> userAreaId.equals(a.getId()))
                    .toList();
        }
        catch (Exception e) { return new ArrayList<>(); }
    }
}