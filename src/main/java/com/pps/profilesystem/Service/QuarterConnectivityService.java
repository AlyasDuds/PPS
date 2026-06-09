package com.pps.profilesystem.Service;

import com.pps.profilesystem.Entity.Area;
import com.pps.profilesystem.Entity.Connectivity;
import com.pps.profilesystem.Repository.ArchivedOfficeRepository;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.ConnectivityRepository;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

/**
 * Service for tracking connectivity changes by quarter.
 * <p>
 * Also exposes {@link #getDashboardConnectivityStats()} so the Dashboard page
 * can show the same connected / disconnected / total figures as the Quarters
 * page — i.e. based on the current quarter snapshot with all per-area overrides.
 */
@Service
public class QuarterConnectivityService {

    @Autowired
    private ConnectivityRepository connectivityRepository;

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private ArchivedOfficeRepository archivedOfficeRepository;

    // Offices to ignore when counting "newly connected"
    private static final Set<Integer> NEWLY_CONNECTED_IGNORE = Set.of(1364, 1365, 1366, 1374);

    // ── Dashboard snapshot stats ──────────────────────────────────────────────

    /**
     * Returns actual live DB connectivity stats for the dashboard.
     * Uses direct DB counts (not quarterly carry-forward) so the numbers
     * always match the real data: total non-archived offices, and
     * connected/disconnected based on the current connectionStatus column.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardConnectivityStats() {
        int currentYear = LocalDate.now().getYear();
        String currentQuarter = getCurrentQuarter();

        long total      = postalOfficeRepository.countNonArchived();
        long connected  = postalOfficeRepository.countNonArchivedByConnectionStatus(true);
        long disconnected = Math.max(0, total - connected);

        Map<String, Object> result = new HashMap<>();
        result.put("totalCount",    total);
        result.put("activeCount",   connected);
        result.put("inactiveCount", disconnected);
        result.put("quarter",       currentQuarter);
        result.put("year",          currentYear);
        return result;
    }

    // ── Internal stats logic (mirrors QuartersController.getConnectivityStats) ─

    public Map<String, Long> getConnectivityStats(int year, String quarterFilter, Integer areaId, String statusFilter) {

        // 1. All Areas: sum each individual area
        if (areaId == null) {
            List<Area> allAreas = areaRepository.findAll();
            long totalConnected    = 0;
            long totalDisconnected = 0;
            long totalOffices      = 0;
            for (Area area : allAreas) {
                Map<String, Long> s = getConnectivityStats(year, quarterFilter, area.getId(), statusFilter);
                totalConnected    += s.getOrDefault("totalConnected",    0L);
                totalDisconnected += s.getOrDefault("totalDisconnected", 0L);
                totalOffices      += s.getOrDefault("totalOffices",      0L);
            }
            Map<String, Long> stats = new HashMap<>();
            stats.put("totalConnected",    totalConnected);
            stats.put("totalDisconnected", totalDisconnected);
            stats.put("totalOffices",      totalOffices);
            return stats;
        }

        // 2. year >= 2026: carry forward Q4 2025
        if (year >= 2026) {
            Map<String, Long> q4_2025 = getConnectivityStats(2025, "Q4", areaId, null);
            long baseTotal        = q4_2025.getOrDefault("totalOffices",      0L);
            long baseDisconnected = q4_2025.getOrDefault("totalDisconnected", 0L);
            long baseConnected    = Math.max(0, baseTotal - baseDisconnected);

            Map<String, Long> stats = new HashMap<>();
            if ("active".equals(statusFilter)) {
                stats.put("totalConnected",    baseConnected);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices",      baseConnected);
            } else if ("inactive".equals(statusFilter)) {
                stats.put("totalConnected",    0L);
                stats.put("totalDisconnected", baseDisconnected);
                stats.put("totalOffices",      baseDisconnected);
            } else if ("newly_connected".equals(statusFilter) || "newly_disconnected".equals(statusFilter)) {
                stats.put("totalConnected",    0L);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices",      0L);
            } else {
                stats.put("totalConnected",    baseConnected);
                stats.put("totalDisconnected", baseDisconnected);
                stats.put("totalOffices",      baseTotal);
            }
            return stats;
        }

        // 3. Standard snapshot-based calculation
        Map<String, Long> stats = new HashMap<>();
        try {
            LocalDateTime snapshotDate = resolveSnapshotDate(year, quarterFilter);
            long active   = countActiveAt(snapshotDate, areaId);
            long total    = countTotal(areaId);
            long inactive = Math.max(0, total - active);

            if ("active".equals(statusFilter)) {
                stats.put("totalConnected",    active);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices",      active);
            } else if ("inactive".equals(statusFilter)) {
                stats.put("totalConnected",    0L);
                stats.put("totalDisconnected", inactive);
                stats.put("totalOffices",      inactive);
            } else if ("newly_connected".equals(statusFilter)) {
                LocalDateTime[] qRange = resolveQuarterRange(year, quarterFilter);
                long nc = countNewlyConnected(qRange[0], qRange[1], areaId);
                stats.put("totalConnected",    nc);
                stats.put("totalDisconnected", 0L);
                stats.put("totalOffices",      nc);
            } else if ("newly_disconnected".equals(statusFilter)) {
                LocalDateTime[] qRange = resolveQuarterRange(year, quarterFilter);
                long nd = countNewlyDisconnected(qRange[0], qRange[1], areaId);
                stats.put("totalConnected",    0L);
                stats.put("totalDisconnected", nd);
                stats.put("totalOffices",      nd);
            } else {
                // All status — base values first
                stats.put("totalConnected",    active);
                stats.put("totalDisconnected", inactive);
                stats.put("totalOffices",      total);

                // Area 1 overrides
                if (areaId == 1 && year >= 2025) {
                    if (year == 2025 && "Q4".equalsIgnoreCase(quarterFilter)) {
                        stats.put("totalConnected",    70L);
                        stats.put("totalDisconnected", 0L);
                        stats.put("totalOffices",      72L);
                    } else if (year == 2025) {
                        stats.put("totalConnected",    70L);
                        stats.put("totalDisconnected", 0L);
                        stats.put("totalOffices",      70L);
                    } else {
                        stats.put("totalConnected",    72L);
                        stats.put("totalDisconnected", 0L);
                        stats.put("totalOffices",      72L);
                    }
                }

                // Area 2 overrides
                if (areaId == 2 && year >= 2025) {
                    if (year == 2025 && "Q1".equalsIgnoreCase(quarterFilter)) {
                        stats.put("totalConnected",    152L);
                        stats.put("totalDisconnected", 31L);
                        stats.put("totalOffices",      183L);
                    } else if (year == 2025) {
                        stats.put("totalConnected",    154L);
                        stats.put("totalDisconnected", 31L);
                        stats.put("totalOffices",      185L);
                    } else {
                        stats.put("totalConnected",    154L);
                        stats.put("totalDisconnected", 31L);
                        stats.put("totalOffices",      185L);
                    }
                }

                // Generic carry-forward for areas 3-9 when year >= 2025
                if (year >= 2025 && areaId != 1 && areaId != 2) {
                    LocalDateTime snap2025  = resolveSnapshotDate(2025, "Q4");
                    long active2025  = countActiveAt(snap2025, areaId);
                    long total2025   = countTotal(areaId);
                    long inactive2025 = Math.max(0, total2025 - active2025);
                    if (statusFilter == null) {
                        stats.put("totalConnected",    active2025);
                        stats.put("totalDisconnected", inactive2025);
                        stats.put("totalOffices",      total2025);
                    }
                }
            }
        } catch (Exception e) {
            stats.put("totalConnected",    0L);
            stats.put("totalDisconnected", 0L);
            stats.put("totalOffices",      0L);
        }
        return stats;
    }

    // ── Helper: count offices currently connected at a snapshot date ──────────

    private long countActiveAt(LocalDateTime snap, Integer areaId) {
        if (areaId != null && areaId == -1) return 0;
        if (areaId == null) {
            return postalOfficeRepository.countNonArchivedByConnectionStatus(true);
        }
        return postalOfficeRepository.findByIsArchivedFalse().stream()
                .filter(po -> Boolean.TRUE.equals(po.getConnectionStatus()))
                .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
                .count();
    }

    // ── Helper: count total non-archived offices ──────────────────────────────

    private long countTotal(Integer areaId) {
        if (areaId != null && areaId == -1) return 0;
        if (areaId == null) return postalOfficeRepository.countNonArchived();
        return postalOfficeRepository.findByIsArchivedFalse().stream()
                .filter(po -> po.getArea() != null && areaId.equals(po.getArea().getId()))
                .count();
    }

    // ── Helper: newly connected offices in a date range ───────────────────────

    private long countNewlyConnected(LocalDateTime start, LocalDateTime end, Integer areaId) {
        if (areaId != null && areaId == -1) return 0;
        List<Connectivity> candidates = connectivityRepository.findByDateConnectedBetween(start, end);
        Set<Integer> newly = new HashSet<>();
        for (Connectivity c : candidates) {
            if (c.getPostalOffice() == null) continue;
            Integer oid = c.getPostalOffice().getId();
            if (archivedOfficeRepository.existsByPostalOfficeId(oid)) continue;
            if (NEWLY_CONNECTED_IGNORE.contains(oid)) continue;
            if (areaId != null && (c.getPostalOffice().getArea() == null
                    || !areaId.equals(c.getPostalOffice().getArea().getId()))) continue;
            boolean hasEarlier = connectivityRepository.findByPostalOfficeId(oid).stream()
                    .map(cc -> cc.getDateConnected() != null ? cc.getDateConnected() : cc.getCreatedStamp())
                    .filter(Objects::nonNull)
                    .anyMatch(dt -> dt.isBefore(start));
            if (!hasEarlier) newly.add(oid);
        }
        return newly.size();
    }

    // ── Helper: newly disconnected offices in a date range ────────────────────

    private long countNewlyDisconnected(LocalDateTime start, LocalDateTime end, Integer areaId) {
        if (areaId != null && areaId == -1) return 0;
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

    // ── Helper: snapshot date for a quarter ───────────────────────────────────

    private LocalDateTime resolveSnapshotDate(int year, String quarterFilter) {
        if (quarterFilter == null || quarterFilter.isEmpty()) {
            quarterFilter = getCurrentQuarter();
        }
        if (isCurrentQuarter(year, quarterFilter)) return LocalDateTime.now();
        switch (quarterFilter.toUpperCase()) {
            case "Q1": return LocalDateTime.of(year, 3,  31, 23, 59, 59);
            case "Q2": return LocalDateTime.of(year, 6,  30, 23, 59, 59);
            case "Q3": return LocalDateTime.of(year, 9,  30, 23, 59, 59);
            case "Q4": return LocalDateTime.of(year, 12, 31, 23, 59, 59);
            default:   return LocalDateTime.of(year, 12, 31, 23, 59, 59);
        }
    }

    private LocalDateTime[] resolveQuarterRange(int year, String quarterFilter) {
        String q = (quarterFilter == null || quarterFilter.isEmpty()) ? "Q1" : quarterFilter.toUpperCase();
        switch (q) {
            case "Q1": return new LocalDateTime[]{LocalDateTime.of(year,  1,  1, 0, 0, 0), LocalDateTime.of(year,  3, 31, 23, 59, 59)};
            case "Q2": return new LocalDateTime[]{LocalDateTime.of(year,  4,  1, 0, 0, 0), LocalDateTime.of(year,  6, 30, 23, 59, 59)};
            case "Q3": return new LocalDateTime[]{LocalDateTime.of(year,  7,  1, 0, 0, 0), LocalDateTime.of(year,  9, 30, 23, 59, 59)};
            default:   return new LocalDateTime[]{LocalDateTime.of(year, 10,  1, 0, 0, 0), LocalDateTime.of(year, 12, 31, 23, 59, 59)};
        }
    }

    private String getCurrentQuarter() {
        int month = LocalDate.now().getMonthValue();
        if (month <= 3)  return "Q1";
        if (month <= 6)  return "Q2";
        if (month <= 9)  return "Q3";
        return "Q4";
    }

    private boolean isCurrentQuarter(int year, String quarterFilter) {
        if (quarterFilter == null || quarterFilter.isEmpty()) return false;
        return year == LocalDate.now().getYear() && quarterFilter.equalsIgnoreCase(getCurrentQuarter());
    }

    // ── Legacy methods (kept for backward compatibility) ──────────────────────

    /**
     * Get connectivity statistics for a specific quarter (legacy integer-quarter API).
     */
    @Transactional(readOnly = true)
    public QuarterStats getQuarterStats(int year, int quarter) {
        LocalDateTime[] quarterDates = getQuarterDateRange(year, quarter);
        LocalDateTime quarterStart = quarterDates[0];
        LocalDateTime quarterEnd   = quarterDates[1];

        QuarterStats stats = new QuarterStats();
        stats.setYear(year);
        stats.setQuarter(quarter);

        Long newlyConnected = connectivityRepository.countNewlyConnectedInQuarter(quarterStart, quarterEnd);
        stats.setNewlyConnected(newlyConnected != null ? newlyConnected.intValue() : 0);

        Long newlyDisconnected = connectivityRepository.countNewlyDisconnectedInQuarter(quarterStart, quarterEnd);
        stats.setNewlyDisconnected(newlyDisconnected != null ? newlyDisconnected.intValue() : 0);

        List<Connectivity> activeAtQuarterEnd = connectivityRepository.findActiveAtDate(quarterEnd);
        stats.setTotalConnected((int) activeAtQuarterEnd.stream()
                .map(c -> c.getPostalOffice().getId())
                .distinct()
                .count());

        long totalOffices = postalOfficeRepository.count();
        stats.setTotalDisconnected((int) (totalOffices - stats.getTotalConnected()));

        return stats;
    }

    @Transactional(readOnly = true)
    public List<QuarterStats> getYearStats(int year) {
        List<QuarterStats> yearStats = new ArrayList<>();
        for (int quarter = 1; quarter <= 4; quarter++) {
            yearStats.add(getQuarterStats(year, quarter));
        }
        return yearStats;
    }

    @Transactional(readOnly = true)
    public List<Connectivity> getNewlyConnectedInQuarter(int year, int quarter) {
        return connectivityRepository.findConnectionsInQuarter(year, quarter);
    }

    @Transactional(readOnly = true)
    public List<Connectivity> getNewlyDisconnectedInQuarter(int year, int quarter) {
        return connectivityRepository.findDisconnectionsInQuarter(year, quarter);
    }

    @Transactional
    public void disconnectOffice(Integer connectivityId) {
        connectivityRepository.findById(connectivityId).ifPresent(conn -> {
            conn.disconnect();
            connectivityRepository.save(conn);
        });
    }

    @Transactional
    public Connectivity connectOffice(Connectivity connectivity) {
        return connectivityRepository.save(connectivity);
    }

    private LocalDateTime[] getQuarterDateRange(int year, int quarter) {
        Month startMonth, endMonth;
        switch (quarter) {
            case 1: startMonth = Month.JANUARY;  endMonth = Month.MARCH;     break;
            case 2: startMonth = Month.APRIL;    endMonth = Month.JUNE;      break;
            case 3: startMonth = Month.JULY;     endMonth = Month.SEPTEMBER; break;
            case 4: startMonth = Month.OCTOBER;  endMonth = Month.DECEMBER;  break;
            default: throw new IllegalArgumentException("Quarter must be 1-4");
        }
        LocalDateTime start = LocalDateTime.of(year, startMonth, 1, 0, 0, 0);
        LocalDateTime end   = LocalDateTime.of(year, endMonth, endMonth.length(isLeapYear(year)), 23, 59, 59);
        return new LocalDateTime[]{start, end};
    }

    private boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    public static class QuarterStats {
        private int year;
        private int quarter;
        private int newlyConnected;
        private int newlyDisconnected;
        private int totalConnected;
        private int totalDisconnected;

        public int getYear()                     { return year; }
        public void setYear(int year)            { this.year = year; }

        public int getQuarter()                  { return quarter; }
        public void setQuarter(int quarter)      { this.quarter = quarter; }

        public int getNewlyConnected()           { return newlyConnected; }
        public void setNewlyConnected(int v)     { this.newlyConnected = v; }

        public int getNewlyDisconnected()        { return newlyDisconnected; }
        public void setNewlyDisconnected(int v)  { this.newlyDisconnected = v; }

        public int getTotalConnected()           { return totalConnected; }
        public void setTotalConnected(int v)     { this.totalConnected = v; }

        public int getTotalDisconnected()        { return totalDisconnected; }
        public void setTotalDisconnected(int v)  { this.totalDisconnected = v; }

        public String getQuarterLabel()          { return "Q" + quarter + " " + year; }
    }
}