package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.Area;
import com.pps.profilesystem.Entity.QuarterlySnapshot;
import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Repository.ArchivedOfficeRepository;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.ConnectivityRepository;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import com.pps.profilesystem.Repository.QuarterlySnapshotRepository;
import com.pps.profilesystem.Repository.UserRepository;
import com.pps.profilesystem.Service.QuarterlySnapshotService;
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

    @Autowired
    private QuarterlySnapshotService snapshotService;

    @Autowired
    private QuarterlySnapshotRepository snapshotRepository;

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

        Integer roleId = currentUser != null ? currentUser.getRole() : null;
        Integer userAreaId = currentUser != null ? currentUser.getAreaId() : null;

        int currentYear = (year != null) ? year : LocalDate.now().getYear();

        // Parse areaId from filter string
        Integer areaId = null;
        if (areaFilter != null && !areaFilter.trim().isEmpty()) {
            try {
                areaId = Integer.parseInt(areaFilter.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        // Apply user area restrictions: non-system-admin users can only see their
        // assigned area
        if (roleId != null && roleId != 1 && roleId != 4) {
            // User is not a system admin, restrict to their assigned area
            if (userAreaId != null) {
                // If no area filter is set, default to user's area
                if (areaId == null) {
                    areaId = userAreaId;
                } else if (!areaId.equals(userAreaId)) {
                    // User is trying to access an area they're not assigned to - redirect to their
                    // area
                    areaId = userAreaId;
                }
            } else {
                // User has no area assigned, show all data
                areaId = null; // treat as no area filter
            }
        }
        // If roleId is null or roleId == 1 (system admin), allow all areas

        model.addAttribute("currentYear", currentYear);
        model.addAttribute("currentQuarterInfo", getCurrentQuarterInfo());
        model.addAttribute("areas", getAllAreas(roleId, userAreaId));

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

        // Derive connectivity stats from quartersData so the top cards always match the
        // table
        model.addAttribute("connectivityStats",
                deriveStatsFromQuarters(quartersData, quarterFilter, statusFilter));

        model.addAttribute("selectedYearFilter", year != null ? String.valueOf(year) : null);
        model.addAttribute("selectedAreaFilter", areaId != null && areaId != -1 ? areaId.toString() : "");
        model.addAttribute("selectedQuarterFilter", quarterFilter);
        model.addAttribute("selectedStatusFilter", statusFilter);
        model.addAttribute("activePage", "report");

        // Add snapshot history data
        List<QuarterlySnapshot> snapshots = snapshotRepository.findByYear(currentYear);
        model.addAttribute("snapshotHistory", snapshots);

        Map<String, Boolean> userAccess = new HashMap<>();
        userAccess.put("can_access_all_areas", roleId != null && (roleId == 1 || roleId == 4));
        model.addAttribute("userAccess", userAccess);
        model.addAttribute("isSystemAdmin", roleId != null && (roleId == 1 || roleId == 4));
        model.addAttribute("isAreaAdmin", roleId != null && roleId == 2);
        model.addAttribute("isAnyAdmin", roleId != null && (roleId == 1 || roleId == 2));

        return "report";
    }

    /**
     * Connectivity stats using the same logic as the Connectivity Report top cards.
     * Keeps Dashboard totals in sync with /report for the same year and quarter.
     */
    public Map<String, Long> computeConnectivityStats(int year, String quarterFilter, Integer areaId) {
        return computeConnectivityStats(year, quarterFilter, areaId, null);
    }

    public Map<String, Long> computeConnectivityStats(
            int year, String quarterFilter, Integer areaId, String statusFilter) {
        List<Map<String, Object>> quartersData = buildQuartersData(year, quarterFilter, areaId, statusFilter);
        return deriveStatsFromQuarters(quartersData, quarterFilter, statusFilter);
    }

    public void addOfficeStatusCounts(Model model, Integer areaId) {
        if (areaId != null && areaId == -1) {
            model.addAttribute("openCount", 0L);
            model.addAttribute("closedCount", 0L);
            return;
        }
        model.addAttribute("openCount", postalOfficeRepository.countOpenOffices(areaId));
        model.addAttribute("closedCount", postalOfficeRepository.countClosedOffices(areaId));
    }

    // ── Build per-quarter rows ────────────────────────────────────────────────

    private void sortNamesList(List<String> list) {
        if (list == null)
            return;
        list.sort(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e));
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> buildQuartersData(
            int year, String quarterFilter, Integer areaId, String statusFilter) {

        String[] quarters = { "Q1", "Q2", "Q3", "Q4" };
        int[][] qMonths = { { 1, 3 }, { 4, 6 }, { 7, 9 }, { 10, 12 } };

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        boolean currentYearMatch = (today.getYear() == year);

        // 1. If areaId is null (All Areas), build by summing all individual areas (1 to
        // 9)
        if (areaId == null) {
            List<Area> allAreas = areaRepository.findAll();
            List<Map<String, Object>> combined = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String q = quarters[i];
                if (quarterFilter != null && !quarterFilter.isEmpty() && !quarterFilter.equalsIgnoreCase(q)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("quarter", q);
                row.put("year", year);

                int currentQIndex = (today.getMonthValue() - 1) / 3;
                boolean isCurrent = currentYearMatch && (i == currentQIndex);
                boolean isFuture = currentYearMatch && (i > currentQIndex);
                row.put("isCurrent", isCurrent);
                row.put("isFuture", isFuture);

                row.put("connected", 0L);
                row.put("newlyConnected", 0L);
                row.put("disconnected", 0L);
                row.put("newlyDisconnected", 0L);
                row.put("total", 0L);
                row.put("totalHint", null);
                row.put("connectedNames", new ArrayList<String>());
                row.put("disconnectedNames", new ArrayList<String>());
                row.put("newlyConnectedNames", new ArrayList<String>());
                row.put("newlyDisconnectedNames", new ArrayList<String>());
                combined.add(row);
            }

            for (Area area : allAreas) {
                Integer aId = area.getId();
                List<Map<String, Object>> areaData = buildQuartersData(year, quarterFilter, aId, statusFilter);
                for (int k = 0; k < combined.size(); k++) {
                    Map<String, Object> combRow = combined.get(k);
                    Map<String, Object> areaRow = areaData.get(k);

                    if (Boolean.TRUE.equals(areaRow.get("isFuture"))) {
                        continue;
                    }

                    combRow.put("connected", toLong(combRow.get("connected")) + toLong(areaRow.get("connected")));
                    combRow.put("newlyConnected",
                            toLong(combRow.get("newlyConnected")) + toLong(areaRow.get("newlyConnected")));
                    combRow.put("disconnected",
                            toLong(combRow.get("disconnected")) + toLong(areaRow.get("disconnected")));
                    combRow.put("newlyDisconnected",
                            toLong(combRow.get("newlyDisconnected")) + toLong(areaRow.get("newlyDisconnected")));
                    // Use global total for "All Areas" instead of summing individual area totals
                    combRow.put("total", countTotal(null));

                    ((List<String>) combRow.get("connectedNames")).addAll((List<String>) areaRow.get("connectedNames"));
                    ((List<String>) combRow.get("disconnectedNames"))
                            .addAll((List<String>) areaRow.get("disconnectedNames"));
                    ((List<String>) combRow.get("newlyConnectedNames"))
                            .addAll((List<String>) areaRow.get("newlyConnectedNames"));
                    ((List<String>) combRow.get("newlyDisconnectedNames"))
                            .addAll((List<String>) areaRow.get("newlyDisconnectedNames"));
                }
            }

            for (Map<String, Object> combRow : combined) {
                if (Boolean.TRUE.equals(combRow.get("isFuture"))) {
                    combRow.put("connected", null);
                    combRow.put("newlyConnected", null);
                    combRow.put("disconnected", null);
                    combRow.put("newlyDisconnected", null);
                    combRow.put("total", null);
                } else {
                    sortNamesList((List<String>) combRow.get("connectedNames"));
                    sortNamesList((List<String>) combRow.get("disconnectedNames"));
                    sortNamesList((List<String>) combRow.get("newlyConnectedNames"));
                    sortNamesList((List<String>) combRow.get("newlyDisconnectedNames"));
                }
            }
            return combined;
        }

        // 2. Carry forward currentYear Q4 for year > currentYear for any non-null
        // areaId
        int currentYear = LocalDate.now().getYear();
        if (year > currentYear) {
            List<Map<String, Object>> lastQ_list = buildQuartersData(currentYear, "Q4", areaId, statusFilter);
            Map<String, Object> lastQ = lastQ_list.get(0);

            long baseConnected = toLong(lastQ.get("connected")) + toLong(lastQ.get("newlyConnected"));
            long baseDisconnected = toLong(lastQ.get("disconnected")) + toLong(lastQ.get("newlyDisconnected"));
            // Use constant total from countTotal instead of calculating from connected/disconnected
            long baseTotal = countTotal(areaId);

            List<String> connNames = new ArrayList<>((List<String>) lastQ.get("connectedNames"));
            connNames.addAll((List<String>) lastQ.get("newlyConnectedNames"));

            List<String> discNames = new ArrayList<>((List<String>) lastQ.get("disconnectedNames"));
            discNames.addAll((List<String>) lastQ.get("newlyDisconnectedNames"));

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String q = quarters[i];
                if (quarterFilter != null && !quarterFilter.isEmpty() && !quarterFilter.equalsIgnoreCase(q)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("quarter", q);
                row.put("year", year);

                int currentQIndex = (today.getMonthValue() - 1) / 3;
                boolean isCurrent = currentYearMatch && (i == currentQIndex);
                boolean isFuture = currentYearMatch && (i > currentQIndex);
                row.put("isCurrent", isCurrent);
                row.put("isFuture", isFuture);

                if (isFuture) {
                    row.put("connected", null);
                    row.put("disconnected", null);
                    row.put("newlyConnected", null);
                    row.put("newlyDisconnected", null);
                    row.put("total", null);
                    row.put("totalHint", null);
                    row.put("connectedNames", new ArrayList<String>());
                    row.put("disconnectedNames", new ArrayList<String>());
                    row.put("newlyConnectedNames", new ArrayList<String>());
                    row.put("newlyDisconnectedNames", new ArrayList<String>());
                } else {
                    row.put("connected", baseConnected);
                    row.put("newlyConnected", 0L);
                    row.put("disconnected", baseDisconnected);
                    row.put("newlyDisconnected", 0L);
                    row.put("total", baseTotal);
                    row.put("totalHint", null);
                    row.put("connectedNames", connNames);
                    row.put("disconnectedNames", discNames);
                    row.put("newlyConnectedNames", new ArrayList<String>());
                    row.put("newlyDisconnectedNames", new ArrayList<String>());
                }
                result.add(row);
            }
            return list;
        }

        List<Map<String, Object>> list = new ArrayList<>();

        // For historical years, use the snapshot at the end of that year (Dec 31 23:59:59)
        // For current year, use the current snapshot
        LocalDateTime snapshotDate = currentYearMatch ? now : LocalDateTime.of(year, 12, 31, 23, 59, 59);

        // Fetch base connected and disconnected for the snapshot date
        List<String> finalConnectedNames = getConnectedNames(snapshotDate, areaId);
        List<String> finalDisconnectedNames = getDisconnectedNames(snapshotDate, areaId);

        // Running totals starting from snapshot and going backwards
        List<String> runningConnectedNames = new ArrayList<>(finalConnectedNames);
        List<String> runningDisconnectedNames = new ArrayList<>(finalDisconnectedNames);

        // Pre-fetch all newly connected/disconnected for each quarter to apply backward
        // logic
        List<List<String>> allNewlyConnected = new ArrayList<>();
        List<List<String>> allNewlyDisconnected = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            LocalDateTime qStart = LocalDateTime.of(year, qMonths[i][0], 1, 0, 0, 0);
            LocalDateTime qEnd = LocalDateTime.of(year, qMonths[i][1],
                    YearMonth.of(year, qMonths[i][1]).lengthOfMonth(), 23, 59, 59);
            // Only use current time (now) for the CURRENT ongoing quarter
            // For completed quarters, use the quarter end date to freeze the data
            boolean isCurrentQuarter = currentYearMatch && !qStart.isAfter(now) && !qEnd.isBefore(now);
            LocalDateTime snapshotEnd = isCurrentQuarter ? now : qEnd;
            allNewlyConnected.add(getNewlyConnectedNames(qStart, snapshotEnd, areaId));
            allNewlyDisconnected.add(getNewlyDisconnectedNames(qStart, snapshotEnd, areaId));
        }

        // We process quarters backwards from Q4 down to Q1
        Map<Integer, Map<String, Object>> rowsByQuarter = new HashMap<>();

        for (int i = 3; i >= 0; i--) {
            String q = quarters[i];
            LocalDateTime qStart = LocalDateTime.of(year, qMonths[i][0], 1, 0, 0, 0);
            LocalDateTime qEnd = LocalDateTime.of(year, qMonths[i][1],
                    YearMonth.of(year, qMonths[i][1]).lengthOfMonth(), 23, 59, 59);

            boolean isCurrent = currentYearMatch && !qStart.isAfter(now) && !qEnd.isBefore(now);
            boolean isFuture = currentYearMatch && qStart.isAfter(now);

            if (isFuture) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("quarter", q);
                row.put("year", year);
                row.put("isCurrent", false);
                row.put("isFuture", true);
                row.put("connected", null);
                row.put("disconnected", null);
                row.put("newlyConnected", null);
                row.put("newlyDisconnected", null);
                rowsByQuarter.put(i, row);
                continue;
            }

            // Check if snapshot exists for this quarter (historical data)
            QuarterlySnapshot snapshot = snapshotService.getSnapshot(year, q, areaId);
            if (snapshot != null && !isCurrent) {
                // Use frozen snapshot data for historical quarters
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("quarter", q);
                row.put("year", year);
                row.put("isCurrent", isCurrent);
                row.put("isFuture", false);
                row.put("connected", snapshot.getConnectedCount());
                row.put("newlyConnected", snapshot.getNewlyConnectedCount());
                row.put("disconnected", snapshot.getDisconnectedCount());
                row.put("newlyDisconnected", snapshot.getNewlyDisconnectedCount());
                row.put("total", snapshot.getTotalOffices());
                row.put("totalHint", null);
                row.put("connectedNames", new ArrayList<String>());
                row.put("disconnectedNames", new ArrayList<String>());
                row.put("newlyConnectedNames", new ArrayList<String>());
                row.put("newlyDisconnectedNames", new ArrayList<String>());
                rowsByQuarter.put(i, row);
                continue;
            }

            List<String> newlyConnectedNames = allNewlyConnected.get(i);
            List<String> newlyDisconnectedNames = allNewlyDisconnected.get(i);

            // Base connected is running connected WITHOUT the newly connected this quarter
            List<String> baseConnectedNames = new ArrayList<>(runningConnectedNames);
            java.util.Set<String> newlySet = new java.util.HashSet<>(newlyConnectedNames);
            baseConnectedNames.removeIf(newlySet::contains);

            // Base disconnected is running disconnected WITHOUT the newly disconnected this
            // quarter
            List<String> baseDisconnectedNames = new ArrayList<>(runningDisconnectedNames);
            java.util.Set<String> newlyDiscSet = new java.util.HashSet<>(newlyDisconnectedNames);
            baseDisconnectedNames.removeIf(newlyDiscSet::contains);

            // Connected at end of quarter = base connected + newly connected
            // Use Set to avoid duplicates
            java.util.Set<String> endOfQuarterConnectedSet = new java.util.HashSet<>(baseConnectedNames);
            endOfQuarterConnectedSet.addAll(newlyConnectedNames);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("quarter", q);
            row.put("year", year);
            row.put("isCurrent", isCurrent);
            row.put("isFuture", false);

            row.put("connectedNames", baseConnectedNames);
            row.put("disconnectedNames", baseDisconnectedNames);
            row.put("newlyConnectedNames", newlyConnectedNames);
            row.put("newlyDisconnectedNames", newlyDisconnectedNames);

            row.put("connected", endOfQuarterConnectedSet.size());
            row.put("newlyConnected", newlyConnectedNames.size());
            row.put("disconnected", baseDisconnectedNames.size());
            row.put("newlyDisconnected", newlyDisconnectedNames.size());
            // Total = total non-archived offices for the area (constant across quarters)
            // This ensures total only changes when new offices are inserted or archived
            long totalOffices = countTotal(areaId);
            row.put("total", totalOffices);
            row.put("totalHint", null);

            rowsByQuarter.put(i, row);

            // Prepare for PREVIOUS quarter
            // When going backwards, we need to add newly DISCONNECTED offices back to the running connected list
            // because they were connected before this quarter
            // We also need to REMOVE newly connected offices from running connected list
            // because they were NOT connected in the previous quarter
            runningConnectedNames = new ArrayList<>(baseConnectedNames);
            runningConnectedNames.addAll(newlyDisconnectedNames);
            // Note: newly connected offices are already excluded from baseConnectedNames,
            // so runningConnectedNames now correctly represents the connected state at the START of this quarter
            // (which is the END of the previous quarter)

            runningDisconnectedNames = new ArrayList<>(baseDisconnectedNames);
            // We DO NOT add newlyConnectedNames to runningDisconnectedNames.
            // This assumes newly connected offices were BRAND NEW and did not exist as
            // disconnected before.
            // This guarantees the disconnected count stays constant backwards.
        }

        // Add to list in forward order
        for (int i = 0; i < 4; i++) {
            if (quarterFilter != null && !quarterFilter.isEmpty() && !quarterFilter.equalsIgnoreCase(quarters[i])) {
                continue;
            }
            if (rowsByQuarter.containsKey(i)) {
                list.add(rowsByQuarter.get(i));
            }
        }

        return list;
    }

    /**
     * Derives the stats-card values directly from the already-built quartersData
     * list
     * so that the top cards always show the same numbers as the table rows.
     *
     * Rules:
     * • If a single quarter is filtered, use that row's values.
     * • If no quarter filter, aggregate across all non-future rows
     * (sum newlyConnected / newlyDisconnected; use the earliest row's
     * connected/disconnected
     * as the baseline since the table shows them per-quarter already).
     */
    private Map<String, Long> deriveStatsFromQuarters(
            List<Map<String, Object>> quartersData, String quarterFilter, String statusFilter, Integer areaId) {

        Map<String, Long> stats = new HashMap<>();

        // Sum up across non-future rows
        long totalConnected = 0;
        long totalDisconnected = 0;
        long totalNewlyConn = 0;
        long totalNewlyDisc = 0;
        long total = 0;
        boolean foundNonFuture = false;

        // When a single quarter is selected, use just that row
        if (quarterFilter != null && !quarterFilter.isEmpty()) {
            for (Map<String, Object> row : quartersData) {
                Boolean isFuture = (Boolean) row.getOrDefault("isFuture", false);
                if (Boolean.TRUE.equals(isFuture))
                    continue;
                totalConnected = toLong(row.get("connected"));
                totalDisconnected = toLong(row.get("disconnected"));
                totalNewlyConn = toLong(row.get("newlyConnected"));
                totalNewlyDisc = toLong(row.get("newlyDisconnected"));
                total = toLong(row.get("total"));
                foundNonFuture = true;
                break; // only one row when filtered
            }
        } else {
            // No quarter filter: show totals of the first (or current) non-future row
            // The table already shows each quarter independently; for the card we show
            // the CURRENT quarter row (isCurrent=true) or the most recent past row.
            Map<String, Object> bestRow = null;
            for (Map<String, Object> row : quartersData) {
                Boolean isFuture = (Boolean) row.getOrDefault("isFuture", false);
                Boolean isCurrent = (Boolean) row.getOrDefault("isCurrent", false);
                if (Boolean.TRUE.equals(isFuture))
                    continue;
                foundNonFuture = true;
                bestRow = row; // last non-future row wins (will be Q4 or latest past quarter)
                if (Boolean.TRUE.equals(isCurrent))
                    break; // current quarter: use it and stop
            }
            if (bestRow != null) {
                totalConnected = toLong(bestRow.get("connected"));
                totalDisconnected = toLong(bestRow.get("disconnected"));
                totalNewlyConn = toLong(bestRow.get("newlyConnected"));
                totalNewlyDisc = toLong(bestRow.get("newlyDisconnected"));
                total = toLong(bestRow.get("total"));
            }
        }

        if (!foundNonFuture) {
            stats.put("totalConnected", 0L);
            stats.put("totalDisconnected", 0L);
            stats.put("totalNewlyConnected", 0L);
            stats.put("totalNewlyDisconnected", 0L);
            stats.put("totalOffices", 0L);
            return stats;
        }

        // Apply status filter overrides
        if ("active".equals(statusFilter)) {
            stats.put("totalConnected", totalConnected);
            stats.put("totalDisconnected", 0L);
            stats.put("totalNewlyConnected", 0L);
            stats.put("totalNewlyDisconnected", 0L);
            stats.put("totalOffices", totalConnected);
        } else if ("newly_connected".equals(statusFilter)) {
            stats.put("totalConnected", totalNewlyConn);
            stats.put("totalDisconnected", 0L);
            stats.put("totalNewlyConnected", totalNewlyConn);
            stats.put("totalNewlyDisconnected", 0L);
            stats.put("totalOffices", totalNewlyConn);
        } else if ("inactive".equals(statusFilter)) {
            stats.put("totalConnected", 0L);
            stats.put("totalDisconnected", totalDisconnected);
            stats.put("totalNewlyConnected", 0L);
            stats.put("totalNewlyDisconnected", 0L);
            stats.put("totalOffices", totalDisconnected);
        } else if ("newly_disconnected".equals(statusFilter)) {
            stats.put("totalConnected", 0L);
            stats.put("totalDisconnected", totalNewlyDisc);
            stats.put("totalNewlyConnected", 0L);
            stats.put("totalNewlyDisconnected", totalNewlyDisc);
            stats.put("totalOffices", totalNewlyDisc);
        } else {
            stats.put("totalConnected", totalConnected);
            stats.put("totalDisconnected", totalDisconnected);
            stats.put("totalNewlyConnected", totalNewlyConn);
            stats.put("totalNewlyDisconnected", totalNewlyDisc);
            stats.put("totalOffices", total);
        }
        return stats;
    }

    private long toLong(Object val) {
        if (val == null)
            return 0L;
        if (val instanceof Long)
            return (Long) val;
        if (val instanceof Integer)
            return ((Integer) val).longValue();
        if (val instanceof Number)
            return ((Number) val).longValue();
        return 0L;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getCurrentQuarterLabel(int month) {
        if (month <= 3)
            return "Q1";
        if (month <= 6)
            return "Q2";
        if (month <= 9)
            return "Q3";
        return "Q4";
    }

    private Map<String, Object> getCurrentQuarterInfo() {
        Map<String, Object> info = new HashMap<>();
        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        info.put("quarter", getCurrentQuarterLabel(month));
        info.put("monthName", now.getMonth().toString());
        return info;
    }

    private List<Area> getAllAreas(Integer roleId, Integer userAreaId) {
        try {
            List<Area> all = areaRepository.findAll();
            if (roleId != null && (roleId == 1 || roleId == 4))
                return all;
            if (userAreaId == null)
                return new ArrayList<>();
            return all.stream()
                    .filter(a -> userAreaId.equals(a.getId()))
                    .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ── Helper: build "ID::Area | Name" entry from a Connectivity record ────────
    // Format: "{officeId}::{areaName} | {officeName}"
    // The ID prefix lets Thymeleaf/JS extract the profile link without a
    // second round-trip. Sorting is done on the "Area | Name" suffix so the
    // list is still alphabetical by area then office name.
    private String toNameEntry(com.pps.profilesystem.Entity.Connectivity c) {
        String area = c.getPostalOffice().getArea() != null
                ? c.getPostalOffice().getArea().getAreaName()
                : "N/A";
        String name = c.getPostalOffice().getName() != null
                ? c.getPostalOffice().getName()
                : "";
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
        java.util.List<com.pps.profilesystem.Entity.Connectivity> candidates = connectivityRepository
                .findByDateConnectedBetween(start, end);

        java.util.Map<Integer, String> map = new java.util.HashMap<>();
        for (com.pps.profilesystem.Entity.Connectivity c : candidates) {
            if (c.getPostalOffice() == null)
                continue;
            Integer oid = c.getPostalOffice().getId();
            if (archivedOfficeRepository.existsByPostalOfficeId(oid))
                continue;
            if (NEWLY_CONNECTED_IGNORE.contains(oid))
                continue;
            if (areaId != null
                    && (c.getPostalOffice().getArea() == null || !areaId.equals(c.getPostalOffice().getArea().getId())))
                continue;
            // Exclude if the office was already active at the start of the period
            if (activeAtStart.contains(oid))
                continue;
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
                        (a, b) -> a))
                .values().stream()
                .filter(entry -> !entry.isEmpty())
                .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
                .collect(java.util.stream.Collectors.toList());
    }

    // ── Helper: count total non-archived offices ──────────────────────────────
    private long countTotal(Integer areaId) {
        if (areaId != null && areaId == -1) return 0;
        if (areaId == null) return postalOfficeRepository.countNonArchived();
        return postalOfficeRepository.findByIsArchivedFalse().stream()
                .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
                .count();
    }

    // ── All active offices at a snapshot date ─────────────────────────────────
    private List<String> getConnectedNames(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return empty list (no access)
        if (areaId != null && areaId == -1) {
            return new ArrayList<>();
        }
        return connectivityRepository.findActiveAtDate(snap).stream()
                .filter(c -> c.getPostalOffice() != null)
                .map(c -> c.getPostalOffice())
                .distinct()
                .filter(po -> !archivedOfficeRepository.existsByPostalOfficeId(po.getId()))
                .filter(po -> areaId == null || (po.getArea() != null && areaId.equals(po.getArea().getId())))
                .filter(po -> !NEWLY_CONNECTED_IGNORE.contains(po.getId()))
                .map(po -> {
                    String area = po.getArea() != null ? po.getArea().getAreaName() : "N/A";
                    String name = po.getName() != null ? po.getName() : "";
                    return po.getId() + "::" + area + " | " + name;
                })
                .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
                .collect(java.util.stream.Collectors.toList());
    }

    // ── All inactive offices at a snapshot date ───────────────────────────────
    private List<String> getDisconnectedNames(LocalDateTime snap, Integer areaId) {
        // If areaId is -1, return empty list (no access)
        if (areaId != null && areaId == -1) {
            return new ArrayList<>();
        }

        java.util.Set<Integer> activeIds = connectivityRepository.findActiveAtDate(snap).stream()
                .filter(c -> c.getPostalOffice() != null)
                .map(c -> c.getPostalOffice().getId())
                .filter(id -> !NEWLY_CONNECTED_IGNORE.contains(id))
                .collect(java.util.stream.Collectors.toSet());

        return postalOfficeRepository.findByIsArchivedFalse().stream()
                .filter(po -> !activeIds.contains(po.getId()))
                .filter(po -> areaId == null || (po.getArea() != null && areaId.equals(po.getArea().getId())))
                .filter(po -> {
                    LocalDateTime firstConn = connectivityRepository.findByOfficeIdOrderByDateConnectedDesc(po.getId())
                            .stream()
                            .map(cc -> cc.getDateConnected())
                            .filter(java.util.Objects::nonNull)
                            .min(LocalDateTime::compareTo)
                            .orElse(null);
                    return firstConn == null || !firstConn.isAfter(snap);
                })
                .map(po -> {
                    String area = po.getArea() != null ? po.getArea().getAreaName() : "N/A";
                    String name = po.getName() != null ? po.getName() : "";
                    return po.getId() + "::" + area + " | " + name;
                })
                .sorted(java.util.Comparator.comparing(e -> e.contains("::") ? e.substring(e.indexOf("::") + 2) : e))
                .collect(java.util.stream.Collectors.toList());
    }
}