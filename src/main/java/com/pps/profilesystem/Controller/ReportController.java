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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Controller
@RequestMapping("/report")
public class ReportController {
    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private ConnectivityRepository connectivityRepository;

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private ArchivedOfficeRepository archivedOfficeRepository;

    @Autowired
    private UserRepository userRepository;

    // Offices to ignore when counting "newly connected" (data exceptions)
    private static final java.util.Set<Integer> NEWLY_CONNECTED_IGNORE = java.util.Set.of(1364, 1365, 1366, 1374);

    @GetMapping
    public String showReportPage(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String areaFilter,
            @RequestParam(required = false) String quarterFilter,
            @RequestParam(required = false) String statusFilter,
            Model model) {

        // Get the logged-in user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User currentUser = userRepository.findByEmail(email).orElse(null);

        Integer roleId = currentUser != null ? currentUser.getRole()   : null;
        Integer userAreaId = currentUser != null ? currentUser.getAreaId() : null;

        int currentYear = (year != null) ? year : LocalDate.now().getYear();

        // Parse areaId from filter string
        Integer areaId = null;
        if (areaFilter != null && !areaFilter.trim().isEmpty()) {
            try { areaId = Integer.parseInt(areaFilter.trim()); } catch (NumberFormatException ignored) {}
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
                // User has no area assigned, show all data
                areaId = null; // treat as no area filter
            }
        }
        // If roleId is null or roleId == 1 (system admin), allow all areas

        model.addAttribute("currentYear",       currentYear);
        model.addAttribute("currentQuarterInfo", getCurrentQuarterInfo());
        model.addAttribute("areas",              getAllAreas(roleId, userAreaId));
        model.addAttribute("connectivityStats",
            getConnectivityStats(currentYear, quarterFilter, areaId, statusFilter));
        List<Map<String, Object>> quartersData = buildQuartersData(currentYear, quarterFilter, areaId, statusFilter);
        logger.info("quartersData size: {}", quartersData != null ? quartersData.size() : 0);
        if (quartersData == null || quartersData.isEmpty()) {
            Map<String, Object> placeholder = new LinkedHashMap<>();
            placeholder.put("year", currentYear);
            placeholder.put("quarter", "N/A");
            placeholder.put("isCurrent", false);
            placeholder.put("isFuture", false);
            placeholder.put("connected", 0);
            placeholder.put("newlyConnected", 0);
            placeholder.put("disconnected", 0);
            placeholder.put("newlyDisconnected", 0);
            placeholder.put("total", 0);
            placeholder.put("totalHint", null);
            placeholder.put("connectedNames", new ArrayList<>());
            placeholder.put("newlyConnectedNames", new ArrayList<>());
            placeholder.put("disconnectedNames", new ArrayList<>());
            placeholder.put("newlyDisconnectedNames", new ArrayList<>());
            quartersData = new ArrayList<>();
            quartersData.add(placeholder);
        }
        model.addAttribute("quartersData", quartersData);

        model.addAttribute("selectedYearFilter",    year != null ? String.valueOf(year) : null);
        model.addAttribute("selectedAreaFilter",    areaId != null && areaId != -1 ? areaId.toString() : "");
        model.addAttribute("selectedQuarterFilter", quarterFilter);
        model.addAttribute("selectedStatusFilter",  statusFilter);
        model.addAttribute("activePage", "report");

        Map<String, Boolean> userAccess = new HashMap<>();
        userAccess.put("can_access_all_areas", roleId != null && (roleId == 1 || roleId == 4));
        model.addAttribute("userAccess", userAccess);
        model.addAttribute("isSystemAdmin", roleId != null && (roleId == 1 || roleId == 4));
        model.addAttribute("isAreaAdmin", roleId != null && roleId == 2);
        model.addAttribute("isAnyAdmin", roleId != null && (roleId == 1 || roleId == 2));

        return "report";
    }

    // ── Build per-quarter rows ────────────────────────────────────────────────

    private List<Map<String, Object>> buildQuartersData(
            int year, String quarterFilter, Integer areaId, String statusFilter) {

        String[] quarters = { "Q1", "Q2", "Q3", "Q4" };
        int[][] qMonths   = { {1,3}, {4,6}, {7,9}, {10,12} };

        LocalDate     today            = LocalDate.now();
        LocalDateTime now              = LocalDateTime.now();
        boolean       currentYearMatch = (today.getYear() == year);

        // Baseline: active/inactive count at Dec 31 of previous year
        LocalDateTime baseline = LocalDateTime.of(year - 1, 12, 31, 23, 59, 59);
        long runningConnected    = countActiveAt(baseline, areaId);
        long runningDisconnected = countInactiveAt(baseline, areaId);

        List<Map<String, Object>> list = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            String q = quarters[i];

            int startMonth = qMonths[i][0];
            int endMonth   = qMonths[i][1];

            LocalDateTime qStart = LocalDateTime.of(year, startMonth, 1, 0, 0, 0);
            LocalDateTime qEnd   = LocalDateTime.of(year, endMonth,
                    YearMonth.of(year, endMonth).lengthOfMonth(), 23, 59, 59);

            boolean isCurrent = currentYearMatch && !qStart.isAfter(now) && !qEnd.isBefore(now);
            boolean isFuture  = currentYearMatch && qStart.isAfter(now);

            if (isFuture) {
                if (quarterFilter == null || quarterFilter.isEmpty()
                        || quarterFilter.equalsIgnoreCase(q)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("quarter",           q);
                    row.put("year",              year);
                    row.put("isCurrent",         false);
                    row.put("isFuture",          true);
                    row.put("connected",         null);
                    row.put("disconnected",      null);
                    row.put("newlyConnected",    null);
                    row.put("newlyDisconnected", null);
                    list.add(row);
                }
                continue;
            }

            LocalDateTime snapshotEnd = isCurrent ? now : qEnd;

            long newlyConnected    = countNewlyConnected(qStart, snapshotEnd, areaId);
            long newlyDisconnected = countNewlyDisconnected(qStart, snapshotEnd, areaId);

            List<String> newlyConnectedNames    = getNewlyConnectedNames(qStart, snapshotEnd, areaId);
            List<String> newlyDisconnectedNames = getNewlyDisconnectedNames(qStart, snapshotEnd, areaId);
            List<String> startConnectedNames    = getConnectedNames(qStart, areaId);
            List<String> endConnectedNames      = getConnectedNames(snapshotEnd, areaId);
            List<String> disconnectedNames      = getDisconnectedNames(snapshotEnd, areaId);

            long startConnected = startConnectedNames.size();
            long endConnected   = endConnectedNames.size();

            // Capture PREVIOUS totals BEFORE updating running totals
            long prevConnected    = runningConnected;
            long prevDisconnected = runningDisconnected;

            if ("Q1".equals(q)) {
                runningConnected = endConnected;
            }
            
            // Update running totals
            if (!"Q1".equals(q)) {
                runningConnected = runningConnected + newlyConnected - newlyDisconnected;
                if (runningConnected < 0) runningConnected = 0;
            }
            runningDisconnected = runningDisconnected + newlyDisconnected;

            // Skip adding to list if quarter filter doesn't match,
            // but still keep running totals updated above
            if (quarterFilter != null && !quarterFilter.isEmpty()
                    && !quarterFilter.equalsIgnoreCase(q)) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("quarter",              q);
            row.put("year",                 year);
            row.put("isCurrent",            isCurrent);
            row.put("isFuture",             false);
            row.put("previousConnected",    prevConnected);
            row.put("previousDisconnected", prevDisconnected);
            row.put("newlyConnectedNames",    newlyConnectedNames);
            row.put("newlyDisconnectedNames", newlyDisconnectedNames);
            row.put("connectedNames",         endConnectedNames);
            row.put("disconnectedNames",      disconnectedNames);

            long quarterTotal = countTotalNonArchived(areaId);
            if (quarterTotal < 0) quarterTotal = 0;

            long actualDisconnected = quarterTotal - endConnected;
            if (actualDisconnected < 0) actualDisconnected = 0;

            // Connected = offices connected at quarter end (active) excluding newly connected
            long connectedWithoutNew = endConnected - newlyConnected;
            if (connectedWithoutNew < 0) connectedWithoutNew = 0;
            // Disconnected = total offices minus active connections (including newly connected)
            long disconnected = quarterTotal - endConnected;
            if (disconnected < 0) disconnected = 0;

            // Populate row with detailed metrics for UI
            row.put("connected", connectedWithoutNew);
            row.put("newlyConnected", newlyConnected);
            row.put("disconnected", disconnected);
            row.put("newlyDisconnected", newlyDisconnected);
            row.put("total", quarterTotal);
            row.put("totalHint", null);

            // Filter connectedNames to exclude newlyConnected offices
            java.util.Set<Integer> newlyIds = new java.util.HashSet<>();
            for (String entry : newlyConnectedNames) {
                try {
                    Integer id = Integer.valueOf(entry.split("::")[0]);
                    newlyIds.add(id);
                } catch (Exception ignored) {}
            }
            java.util.List<String> filteredConnectedNames = endConnectedNames.stream()
                .filter(name -> {
                    try {
                        Integer id = Integer.valueOf(name.split("::")[0]);
                        return !newlyIds.contains(id);
                    } catch (Exception e) { return true; }
                })
                .toList();
            // Replace the original list in the map
            row.put("connectedNames", filteredConnectedNames);
            // Keep other name lists as is
            row.put("newlyConnectedNames", newlyConnectedNames);
            row.put("disconnectedNames", disconnectedNames);
            row.put("newlyDisconnectedNames", newlyDisconnectedNames);
            
            list.add(row);
        }
        return list;
    }

    // ── Stats cards ───────────────────────────────────────────────────────────

    private Map<String, Long> getConnectivityStats(
            int year, String quarterFilter, Integer areaId, String statusFilter) {

        Map<String, Long> stats = new HashMap<>();
        try {
            LocalDateTime snap   = resolveSnapshotDate(year, quarterFilter);
            long active   = countActiveAt(snap, areaId);
            long total    = countTotalNonArchived(areaId);
            long inactive = total - active;
            if (inactive < 0) inactive = 0;

            long newlyConnected = countNewlyConnected(snap.minusMonths(3), snap, areaId);
            long newlyDisconnected = countNewlyDisconnected(snap.minusMonths(3), snap, areaId);

            if ("active".equals(statusFilter)) {
                stats.put("totalConnected", active);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices", active);
            } else if ("newly_connected".equals(statusFilter)) {
                stats.put("totalConnected", newlyConnected);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices", newlyConnected);
            } else if ("inactive".equals(statusFilter)) {
                stats.put("totalConnected", 0L);
                stats.put("totalDisconnected", inactive);
                stats.put("totalOffices", inactive);
            } else if ("newly_disconnected".equals(statusFilter)) {
                stats.put("totalConnected", 0L);
                stats.put("totalDisconnected", newlyDisconnected);
                stats.put("totalOffices", newlyDisconnected);
            } else {
                stats.put("totalConnected",    active);
                stats.put("totalDisconnected", inactive);
                stats.put("totalOffices",      total);
            }
        } catch (Exception e) {
            stats.put("totalConnected",    0L);
            stats.put("totalDisconnected", 0L);
            stats.put("totalOffices",      0L);
        }
        return stats;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDateTime resolveSnapshotDate(int year, String quarterFilter) {
        LocalDateTime now   = LocalDateTime.now();
        int           today = java.time.LocalDate.now().getYear();

        // For the current year + current (or unspecified) quarter, snapshot at NOW
        // so future quarters don't pull zero-data into the stats cards
        String currentQ = getCurrentQuarterLabel(java.time.LocalDate.now().getMonthValue());
        boolean isCurrentPeriod = (year == today)
            && (quarterFilter == null || quarterFilter.isEmpty()
                || quarterFilter.equalsIgnoreCase(currentQ));

        if (isCurrentPeriod) return now;

        if (quarterFilter == null || quarterFilter.isEmpty())
            return LocalDateTime.of(year, 12, 31, 23, 59, 59);
        switch (quarterFilter.toUpperCase()) {
            case "Q1": return LocalDateTime.of(year,  3, 31, 23, 59, 59);
            case "Q2": return LocalDateTime.of(year,  6, 30, 23, 59, 59);
            case "Q3": return LocalDateTime.of(year,  9, 30, 23, 59, 59);
            case "Q4": return LocalDateTime.of(year, 12, 31, 23, 59, 59);
            default:   return LocalDateTime.of(year, 12, 31, 23, 59, 59);
        }
    }

    private long countActiveAt(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        // Use current real-time status
        if (areaId == null) {
            return postalOfficeRepository.countNonArchivedByConnectionStatus(true);
        }
        return postalOfficeRepository.findByIsArchivedFalse().stream()
            .filter(po -> Boolean.TRUE.equals(po.getConnectionStatus()))
            .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
            .count();
    }

    // Count offices that were DISCONNECTED at the given snapshot date.
    // Modified to use current real-time status
    private long countInactiveAt(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        // Use current real-time status
        if (areaId == null) {
            return postalOfficeRepository.countNonArchivedByConnectionStatus(false);
        }
        return postalOfficeRepository.findByIsArchivedFalse().stream()
            .filter(po -> !Boolean.TRUE.equals(po.getConnectionStatus()))
            .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
            .count();
    }

    private long countNewlyConnected(LocalDateTime start, LocalDateTime end, Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) {
            return 0;
        }
        // Only count offices whose FIRST-known connectivity is inside the period.
        // This avoids counting offices that already had an earlier connectivity record
        // but also have a (possibly duplicate) record inside the quarter.
        // Skip offices that were already active at the start of the period
        java.util.Set<Integer> activeAtStart = connectivityRepository.findActiveAtDate(start).stream()
                .filter(c -> c.getPostalOffice() != null)
                .map(c -> c.getPostalOffice().getId())
                .collect(java.util.stream.Collectors.toSet());
        java.util.List<com.pps.profilesystem.Entity.Connectivity> candidates = connectivityRepository.findByDateConnectedBetween(start, end);
        java.util.Set<Integer> newly = new java.util.HashSet<>();
        for (com.pps.profilesystem.Entity.Connectivity c : candidates) {
            if (c.getPostalOffice() == null) continue;
            Integer oid = c.getPostalOffice().getId();
            if (archivedOfficeRepository.existsByPostalOfficeId(oid)) continue;
            if (NEWLY_CONNECTED_IGNORE.contains(oid)) continue;
            if (areaId != null && (c.getPostalOffice().getArea() == null || !areaId.equals(c.getPostalOffice().getArea().getId()))) continue;
            // Exclude if office was already active at period start
            if (activeAtStart.contains(oid)) continue;
            // check for any earlier connectivity for this office
            boolean hasEarlier = connectivityRepository.findByPostalOfficeId(oid).stream()
                .map(cc -> cc.getDateConnected() != null ? cc.getDateConnected() : cc.getCreatedStamp())
                .filter(java.util.Objects::nonNull)
                .anyMatch(dt -> dt.isBefore(start));
            if (!hasEarlier) newly.add(oid);
        }
        return newly.size();
    }

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

    private long countTotalNonArchived(Integer areaId) {
        // If areaId is -1, return 0 (no access)
        if (areaId != null && areaId == -1) return 0;
        // Fast native query exists in PostalOfficeRepository
        return postalOfficeRepository.countNonArchivedByArea(areaId);
    }

    private String getCurrentQuarterLabel(int month) {
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    private Map<String, Object> getCurrentQuarterInfo() {
        Map<String, Object> info = new HashMap<>();
        LocalDate now   = LocalDate.now();
        int month       = now.getMonthValue();
        info.put("quarter",   getCurrentQuarterLabel(month));
        info.put("monthName", now.getMonth().toString());
        return info;
    }

    private List<Area> getAllAreas(Integer roleId, Integer userAreaId) {
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

    // ── Helper: build "ID::Area | Name" entry from a Connectivity record ────────
    // Format: "{officeId}::{areaName} | {officeName}"
    // The ID prefix lets Thymeleaf/JS extract the profile link without a
    // second round-trip.  Sorting is done on the "Area | Name" suffix so the
    // list is still alphabetical by area then office name.
    private String toNameEntry(com.pps.profilesystem.Entity.Connectivity c) {
        String area = c.getPostalOffice().getArea() != null
            ? c.getPostalOffice().getArea().getAreaName() : "N/A";
        String name = c.getPostalOffice().getName() != null
            ? c.getPostalOffice().getName() : "";
        return c.getPostalOffice().getId() + "::" + area + " | " + name;
    }

    private List<String> getNewlyConnectedNames(LocalDateTime start, LocalDateTime end, Integer areaId) {
        // If areaId is -1, return empty list (no access)
        if (areaId != null && areaId == -1) {
            return new java.util.ArrayList<>();
        }
        // Determine offices that were already active at the start of the period
        java.util.Set<Integer> activeAtStart = connectivityRepository.findActiveAtDate(start).stream()
                .filter(c -> c.getPostalOffice() != null)
                .map(c -> c.getPostalOffice().getId())
                .collect(java.util.stream.Collectors.toSet());
        java.util.List<com.pps.profilesystem.Entity.Connectivity> candidates = connectivityRepository.findByDateConnectedBetween(start, end);

        java.util.Map<Integer, String> map = new java.util.HashMap<>();
        for (com.pps.profilesystem.Entity.Connectivity c : candidates) {
            if (c.getPostalOffice() == null) continue;
            Integer oid = c.getPostalOffice().getId();
            if (archivedOfficeRepository.existsByPostalOfficeId(oid)) continue;
            if (NEWLY_CONNECTED_IGNORE.contains(oid)) continue;
            if (areaId != null && (c.getPostalOffice().getArea() == null || !areaId.equals(c.getPostalOffice().getArea().getId()))) continue;
            // Exclude if the office was already active at the start of the period
            if (activeAtStart.contains(oid)) continue;
            // skip if there exists an earlier connectivity record
            boolean hasEarlier = connectivityRepository.findByPostalOfficeId(oid).stream()
                    .map(cc -> cc.getDateConnected() != null ? cc.getDateConnected() : cc.getCreatedStamp())
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(dt -> dt.isBefore(start));

            if (!hasEarlier) {
                map.putIfAbsent(oid, toNameEntry(c));
            }
        }

        return map.values().stream()
            .filter(entry -> !entry.isEmpty())
            .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<String> getNewlyDisconnectedNames(LocalDateTime start, LocalDateTime end, Integer areaId) {
        // If areaId is -1, return empty list (no access)
        if (areaId != null && areaId == -1) {
            return new ArrayList<>();
        }
        return connectivityRepository.findByDateDisconnectedBetween(start, end).stream()
            .filter(c -> c.getPostalOffice() != null
                && !archivedOfficeRepository.existsByPostalOfficeId(c.getPostalOffice().getId()))
            .filter(c -> areaId == null
                || (c.getPostalOffice().getArea() != null
                    && areaId.equals(c.getPostalOffice().getArea().getId())))
            .collect(java.util.stream.Collectors.toMap(
                c -> c.getPostalOffice().getId(),
                this::toNameEntry,
                (a, b) -> a
            ))
            .values().stream()
            .filter(entry -> !entry.isEmpty())
            .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
            .collect(java.util.stream.Collectors.toList());
    }

    // ── All active offices at a snapshot date ─────────────────────────────────
    // Modified to use current real-time status
    private List<String> getConnectedNames(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return empty list (no access)
        if (areaId != null && areaId == -1) {
            return new ArrayList<>();
        }
        return postalOfficeRepository.findByIsArchivedFalse().stream()
            .filter(po -> Boolean.TRUE.equals(po.getConnectionStatus()))
            .filter(po -> areaId == null || (po.getArea() != null && areaId.equals(po.getArea().getId())))
            .map(po -> {
                String area = po.getArea() != null ? po.getArea().getAreaName() : "N/A";
                String name = po.getName() != null ? po.getName() : "";
                return po.getId() + "::" + area + " | " + name;
            })
            .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
            .collect(java.util.stream.Collectors.toList());
    }

    // ── All inactive offices at a snapshot date ───────────────────────────────
    // Modified to use current real-time status
    private List<String> getDisconnectedNames(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return empty list (no access)
        if (areaId != null && areaId == -1) {
            return new ArrayList<>();
        }
        return postalOfficeRepository.findByIsArchivedFalse().stream()
            .filter(po -> !Boolean.TRUE.equals(po.getConnectionStatus()))
            .filter(po -> areaId == null || (po.getArea() != null && areaId.equals(po.getArea().getId())))
            .map(po -> {
                String area = po.getArea() != null ? po.getArea().getAreaName() : "N/A";
                String name = po.getName() != null ? po.getName() : "";
                return po.getId() + "::" + area + " | " + name;
            })
            .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
            .collect(java.util.stream.Collectors.toList());
    }
}