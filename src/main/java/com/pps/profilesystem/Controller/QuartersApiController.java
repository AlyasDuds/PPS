package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.PostalOffice;
import com.pps.profilesystem.Entity.User;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import com.pps.profilesystem.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/quarters")
public class QuartersApiController {

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReportController reportController;

    /**
     * Main endpoint for the Quarters page table.
     * Filters offices by year, quarter, area, and status.
     */
    @GetMapping("/post-offices")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getQuartersPostOffices(
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String status) {

        try {
            // Get the logged-in user and apply area restrictions
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            User currentUser = userRepository.findByEmail(email).orElse(null);

            Integer roleId = currentUser != null ? currentUser.getRole() : null;
            Integer userAreaId = currentUser != null ? currentUser.getAreaId() : null;

            // Parse params
            Integer yearInt = parseInteger(year);
            Integer quarterInt = parseQuarter(quarter);
            Integer areaInt = parseInteger(area);

            // Apply user area restrictions: non-system-admin users can only see their assigned area
            if (roleId != null && roleId != 1 && roleId != 4) {
                // User is not a system admin, restrict to their assigned area
                if (userAreaId != null) {
                    // If no area filter is set, default to user's area
                    if (areaInt == null) {
                        areaInt = userAreaId;
                    } else if (!areaInt.equals(userAreaId)) {
                        // User is trying to access an area they're not assigned to - redirect to their area
                        areaInt = userAreaId;
                    }
                } else {
                    // User has no area assigned, return empty list
                    return new ArrayList<>();
                }
            }

            int resolvedYear = (yearInt != null) ? yearInt : LocalDateTime.now().getYear();
            int resolvedQuarter = (quarterInt != null) ? quarterInt : (LocalDateTime.now().getMonthValue() - 1) / 3 + 1;

            // Call reportController.buildQuartersData to get the exact historical classification of offices
            List<Map<String, Object>> quartersData = reportController.buildQuartersData(
                    resolvedYear, "Q" + resolvedQuarter, areaInt, null);

            if (quartersData == null || quartersData.isEmpty()) {
                return new ArrayList<>();
            }

            Map<String, Object> qData = quartersData.get(0);
            Boolean isFuture = (Boolean) qData.getOrDefault("isFuture", false);
            if (Boolean.TRUE.equals(isFuture)) {
                return new ArrayList<>();
            }

            @SuppressWarnings("unchecked")
            List<String> connectedNames = (List<String>) qData.getOrDefault("connectedNames", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> newlyConnectedNames = (List<String>) qData.getOrDefault("newlyConnectedNames", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> disconnectedNames = (List<String>) qData.getOrDefault("disconnectedNames", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> newlyDisconnectedNames = (List<String>) qData.getOrDefault("newlyDisconnectedNames", new ArrayList<>());

            // Build a map of office ID -> status and newThisQuarter flags based on the requested status filter
            Map<Integer, Map<String, Object>> officeConfig = new HashMap<>();

            if (status == null || status.trim().isEmpty() || "active".equals(status)) {
                for (String nameEntry : connectedNames) {
                    try {
                        int id = Integer.parseInt(nameEntry.split("::")[0]);
                        Map<String, Object> config = new HashMap<>();
                        config.put("status", true);
                        config.put("newThisQuarter", false);
                        officeConfig.put(id, config);
                    } catch (Exception ignored) {}
                }
            }
            if (status == null || status.trim().isEmpty() || "newly_connected".equals(status)) {
                for (String nameEntry : newlyConnectedNames) {
                    try {
                        int id = Integer.parseInt(nameEntry.split("::")[0]);
                        Map<String, Object> config = new HashMap<>();
                        config.put("status", true);
                        config.put("newThisQuarter", true);
                        officeConfig.put(id, config);
                    } catch (Exception ignored) {}
                }
            }
            if (status == null || status.trim().isEmpty() || "inactive".equals(status)) {
                for (String nameEntry : disconnectedNames) {
                    try {
                        int id = Integer.parseInt(nameEntry.split("::")[0]);
                        Map<String, Object> config = new HashMap<>();
                        config.put("status", false);
                        config.put("newThisQuarter", false);
                        officeConfig.put(id, config);
                    } catch (Exception ignored) {}
                }
            }
            if (status == null || status.trim().isEmpty() || "newly_disconnected".equals(status)) {
                for (String nameEntry : newlyDisconnectedNames) {
                    try {
                        int id = Integer.parseInt(nameEntry.split("::")[0]);
                        Map<String, Object> config = new HashMap<>();
                        config.put("status", false);
                        config.put("newThisQuarter", false);
                        officeConfig.put(id, config);
                    } catch (Exception ignored) {}
                }
            }

            if (officeConfig.isEmpty()) {
                return new ArrayList<>();
            }

            List<PostalOffice> officesList = postalOfficeRepository.findAllById(officeConfig.keySet());

            return officesList.stream().map(po -> {
                Map<String, Object> dto = convertToDTO(po);
                Map<String, Object> config = officeConfig.get(po.getId());
                if (config != null) {
                    dto.put("status", config.get("status"));
                    dto.put("newThisQuarter", config.get("newThisQuarter"));
                }
                return dto;
            }).collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error in getQuartersPostOffices: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportReport(
            @RequestParam String type,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String areaFilter,
            @RequestParam(required = false) String quarterFilter,
            @RequestParam(required = false) String statusFilter) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Export feature coming soon for type: " + type);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> convertToDTO(PostalOffice po) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",       po.getId());
        dto.put("name",     po.getName());
        dto.put("address",  po.getAddress());
        dto.put("zipCode",  po.getZipCode());
        dto.put("postmaster", po.getPostmaster());
        dto.put("speed",    po.getSpeed());
        dto.put("status",   po.getConnectionStatus());
        dto.put("officeStatus", po.getOfficeStatus());
        dto.put("areaId",   po.getArea() != null ? po.getArea().getId() : null);
        dto.put("area",     po.getArea() != null ? po.getArea().getAreaName() : null);
        dto.put("regionId", po.getRegion() != null ? po.getRegion().getId() : null);
        dto.put("region",   po.getRegion() != null ? po.getRegion().getName() : null);
        dto.put("province", po.getProvince() != null ? po.getProvince().getName() : null);
        dto.put("cityMunicipality", po.getCityMunicipality() != null ? po.getCityMunicipality().getName() : null);
        dto.put("noOfEmployees",    po.getNoOfEmployees());
        dto.put("noOfPostalTellers",  po.getNoOfPostalTellers());
        dto.put("noOfLetterCarriers", po.getNoOfLetterCarriers());
        dto.put("postalOfficeContactPerson", po.getPostalOfficeContactPerson());
        dto.put("postalOfficeContactNumber", po.getPostalOfficeContactNumber());
        dto.put("internetServiceProvider",   po.getInternetServiceProvider());
        dto.put("typeOfConnection",  po.getTypeOfConnection());
        dto.put("staticIpAddress",   po.getStaticIpAddress());
        dto.put("ispContactPerson",  po.getIspContactPerson());
        dto.put("ispContactNumber",  po.getIspContactNumber());
        dto.put("latitude",  po.getLatitude());
        dto.put("longitude", po.getLongitude());
        dto.put("remarks",   po.getRemarks());
        return dto;
    }

    private Integer parseInteger(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try { return Integer.parseInt(val.trim()); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseQuarter(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        String q = val.trim().toUpperCase();
        if (q.startsWith("Q")) {
            try {
                int n = Integer.parseInt(q.substring(1));
                return (n >= 1 && n <= 4) ? n : null;
            } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}