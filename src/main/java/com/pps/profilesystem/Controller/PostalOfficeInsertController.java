package com.pps.profilesystem.Controller;

import com.pps.profilesystem.DTO.BarangayDTO;
import com.pps.profilesystem.DTO.CityMunicipalityDTO;
import com.pps.profilesystem.DTO.ProvinceDTO;
import com.pps.profilesystem.Entity.*;
import com.pps.profilesystem.DTO.ConnectivityNotification;
import com.pps.profilesystem.Service.ConnectivityNotificationService;
import com.pps.profilesystem.Service.LocationHierarchyService;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import com.pps.profilesystem.Repository.ConnectivityRepository;
import com.pps.profilesystem.Repository.ProviderRepository;
import com.pps.profilesystem.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/postal")
public class PostalOfficeInsertController {

    private static final Logger log = LoggerFactory.getLogger(PostalOfficeInsertController.class);
    private static final int INSERT_TX_TIMEOUT_SEC = 20;

    @Autowired private PostalOfficeRepository postalOfficeRepository;
    @Autowired private LocationHierarchyService locationService;
    @Autowired private ConnectivityNotificationService notifService;
    @Autowired private ConnectivityRepository connectivityRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    // ── Location lookup endpoints ─────────────────────────────────────────────

    @GetMapping("/areas")
    public ResponseEntity<?> getAllAreas(Authentication auth) {
        try {
            List<Area> visibleAreas = getVisibleAreasForUser(auth);
            List<Map<String, Object>> result = visibleAreas.stream()
                .map(a -> { Map<String, Object> m = new HashMap<>(); m.put("id", a.getId()); m.put("name", a.getAreaName()); return m; })
                .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) { return err("Failed to load areas: " + e.getMessage()); }
    }

    /**
     * Compatibility endpoint for UIs that must always show the complete area list.
     * (Some pages may have role-based filtering elsewhere; this endpoint is explicit.)
     */
    @GetMapping("/areas/all")
    public ResponseEntity<?> getAllAreasUnfiltered(Authentication auth) {
        // Legacy endpoint; now aligned with role visibility policy.
        return getAllAreas(auth);
    }

    @GetMapping("/regions")
    public ResponseEntity<?> getAllRegions() {
        try {
            List<Map<String, Object>> result = locationService.getAllRegions().stream()
                .map(r -> { Map<String, Object> m = new HashMap<>(); m.put("id", r.getId()); m.put("name", r.getName()); return m; })
                .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) { return err("Failed to load regions: " + e.getMessage()); }
    }

    @GetMapping("/provinces/by-region/{regionId}")
    public ResponseEntity<?> getProvincesByRegion(@PathVariable Integer regionId) {
        try {
            List<ProvinceDTO> dtos = locationService.getProvincesByRegion(regionId).stream()
                .map(p -> new ProvinceDTO(p.getId(), p.getName())).collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) { return err("Failed to load provinces: " + e.getMessage()); }
    }

    @GetMapping("/provinces/by-area/{areaId}")
    public ResponseEntity<?> getProvincesByArea(@PathVariable Integer areaId) {
        try {
            List<ProvinceDTO> dtos = locationService.getProvincesByArea(areaId).stream()
                    .map(p -> new ProvinceDTO(p.getId(), p.getName()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) { return err("Failed to load provinces: " + e.getMessage()); }
    }

    @GetMapping("/cities/by-province/{provinceId}")
    public ResponseEntity<?> getCitiesByProvince(@PathVariable Integer provinceId) {
        try {
            List<CityMunicipalityDTO> dtos = locationService.getCitiesByProvince(provinceId).stream()
                .map(c -> new CityMunicipalityDTO(c.getId(), c.getName())).collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) { return err("Failed to load cities: " + e.getMessage()); }
    }

    @GetMapping("/barangays/by-city/{cityId}")
    public ResponseEntity<?> getBarangaysByCity(@PathVariable Integer cityId) {
        try {
            List<BarangayDTO> dtos = locationService.getBarangaysByCity(cityId).stream()
                .map(b -> new BarangayDTO(b.getId(), b.getName())).collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        } catch (Exception e) { return err("Failed to load barangays: " + e.getMessage()); }
    }

    // ── INSERT ────────────────────────────────────────────────────────────────

    @PostMapping("/postal-office/insert")
    public ResponseEntity<Map<String, Object>> insertPostalOffice(
            @RequestBody Map<String, Object> requestData, Authentication auth) {
        try {
            String name = strVal(requestData.get("name"));
            if (name == null) {
                return insertError("Post office name is required.");
            }

            // Location lookups outside a DB write transaction (keeps connection hold short)
            PostalOffice office = buildPostalOfficeFromRequest(requestData);
            if (office.getName() == null || office.getName().isBlank()) {
                office.setName(name);
            }

            PostalOffice saved = persistOffice(office);
            saveConnectivityAfterOffice(saved, requestData);

            // Notification after commit — avoids holding insert connection during SSE broadcast
            try {
                String actor = actor(auth);
                Integer actorRoleId = ConnectivityNotificationService.roleIdFromAuthorities(
                        auth != null ? auth.getAuthorities() : null
                );
                boolean hasConn = Boolean.TRUE.equals(saved.getIsConnected());
                String notifyName = saved.getName();
                if (notifyName != null && notifyName.length() > 512) {
                    notifyName = notifyName.substring(0, 512);
                }
                Integer areaId = saved.getArea() != null ? saved.getArea().getId() : null;
                notifService.pushAudit(
                        hasConn ? ConnectivityNotification.Type.CONNECTED : ConnectivityNotification.Type.NEW,
                        notifyName != null ? notifyName : name,
                        saved.getId(),
                        actor,
                        actorRoleId,
                        buildInsertDetail(saved, requestData),
                        null,
                        "CONNECTIVITY",
                        "PostalOffice",
                        saved.getId() != null ? saved.getId().longValue() : null,
                        areaId
                );
            } catch (Exception notifEx) {
                log.warn("Insert office {}: notification not sent: {}", saved.getId(), notifEx.getMessage());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Postal office added successfully");
            response.put("id",      saved.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Insert postal office failed", e);
            String msg = rootMessage(e);
            if (isLockTimeout(e)) {
                msg = "Database is busy (lock timeout). Stop the app, restart MySQL, then try again. (" + msg + ")";
            }
            return insertError("Failed to add postal office: " + msg);
        }
    }

    /** Save office only — short transaction, releases locks quickly. */
    private PostalOffice persistOffice(PostalOffice office) {
        try {
            return doPersistOffice(office);
        } catch (Exception e) {
            if (isLockTimeout(e)) {
                log.warn("Lock wait on postal_offices insert — retrying once");
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return doPersistOffice(office);
            }
            throw e;
        }
    }

    private PostalOffice doPersistOffice(PostalOffice office) {
        TransactionTemplate tx = newTransaction(INSERT_TX_TIMEOUT_SEC);
        PostalOffice saved = tx.execute(status -> postalOfficeRepository.save(office));
        if (saved == null) {
            throw new IllegalStateException("Failed to save postal office");
        }
        return saved;
    }

    private static boolean isLockTimeout(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && msg.toLowerCase().contains("lock wait timeout")) {
                return true;
            }
        }
        return false;
    }

    /** Connectivity/plan data in a separate transaction so office insert is not blocked. */
    private void saveConnectivityAfterOffice(PostalOffice saved, Map<String, Object> requestData) {
        TransactionTemplate tx = newTransaction(INSERT_TX_TIMEOUT_SEC);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            tx.executeWithoutResult(status -> {
                PostalOffice managed = postalOfficeRepository.findById(saved.getId())
                        .orElseThrow(() -> new IllegalStateException("Office not found after save: " + saved.getId()));
                saveConnectivityRecord(managed, requestData);
            });
        } catch (Exception connEx) {
            log.warn("Insert office {}: connectivity row not saved: {}", saved.getId(), connEx.getMessage());
        }
    }

    private TransactionTemplate newTransaction(int timeoutSec) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setTimeout(timeoutSec);
        return tx;
    }

    private ResponseEntity<Map<String, Object>> insertError(String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    private static String rootMessage(Throwable t) {
        if (t == null) return "Unknown error";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : root.getClass().getSimpleName();
    }

    // ── Connectivity record with plan/billing ─────────────────────────────────

    private void saveConnectivityRecord(PostalOffice savedOffice, Map<String, Object> req) {
        String planName     = strVal(req.get("planName"));
        String accountNum   = strVal(req.get("accountNumber"));
        String planContract = strVal(req.get("planContract"));
        Object planPriceRaw = req.get("planPrice");
        String ownedShared  = strVal(req.get("ownedOrShared"));
        boolean hasPlanPrice = planPriceRaw != null && !planPriceRaw.toString().trim().isEmpty();

        // Always create connectivity record for tracking purposes
        // Even if inactive, we need a record to track status changes
        Provider provider = getOrCreateDefaultProvider();

        Connectivity conn = new Connectivity();
        conn.setPostalOffice(savedOffice);
        conn.setProvider(provider);
        conn.setOfficeName(savedOffice.getName());
        conn.setArea(savedOffice.getArea());
        conn.setIsWired(parseBool(req.get("isWired"), false));
        conn.setIsWireless(parseBool(req.get("isWireless"), false));
        conn.setIsFree(parseBool(req.get("isFree"), false));

        boolean isSharedVal = parseBool(req.get("isShared"), false);
        if (!isSharedVal && ownedShared != null) {
            isSharedVal = "Shared".equalsIgnoreCase(ownedShared);
        }
        conn.setIsShared(isSharedVal);

        if (planName != null) conn.setPlanName(planName);
        if (accountNum != null) conn.setAccountNumber(accountNum);
        if (planContract != null) conn.setPlanContract(planContract);
        if (hasPlanPrice) {
            try { conn.setPlanPrice(new BigDecimal(planPriceRaw.toString().trim())); } catch (Exception ignored) {}
        }

        LocalDateTime connected = parseDateTime(req.get("dateConnected"));
        LocalDateTime disconnected = parseDateTime(req.get("dateDisconnected"));
        
        // Set dateConnected - use current date if not provided
        if (connected != null) {
            conn.setDateConnected(connected);
        } else {
            conn.setDateConnected(LocalDateTime.now());
        }
        
        // Set dateDisconnected if inactive or explicitly provided
        if (!Boolean.TRUE.equals(savedOffice.getIsConnected())) {
            // Office is inactive - set dateDisconnected to now if not provided
            if (disconnected != null) {
                conn.setDateDisconnected(disconnected);
            } else {
                conn.setDateDisconnected(LocalDateTime.now());
            }
        } else if (disconnected != null) {
            // Office is active but dateDisconnected was provided (edge case)
            conn.setDateDisconnected(disconnected);
        }

        Connectivity savedConn = connectivityRepository.save(conn);

        // Only set active connectivity if office is currently connected
        if (Boolean.TRUE.equals(savedOffice.getIsConnected())) {
            savedOffice.setActiveConnectivity(savedConn);
            postalOfficeRepository.save(savedOffice);
        }
    }

    private Provider resolveDefaultProvider() {
        return providerRepository.findByName("Default Provider");
    }

    private Provider getOrCreateDefaultProvider() {
        Provider existing = resolveDefaultProvider();
        if (existing != null) {
            return existing;
        }
        TransactionTemplate tx = newTransaction(10);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> {
            Provider again = resolveDefaultProvider();
            if (again != null) {
                return again;
            }
            Provider p = new Provider();
            p.setName("Default Provider");
            p.setCreatedBy(1L);
            return providerRepository.save(p);
        });
    }

    private static boolean parseBool(Object value, boolean defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(value.toString().trim());
    }

    private static LocalDateTime parseDateTime(Object value) {
        String s = value == null ? null : value.toString().trim();
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildInsertDetail(PostalOffice saved, Map<String, Object> req) {
        StringBuilder sb = new StringBuilder("New office added");
        
        // Connectivity fields
        String isp = saved.getInternetServiceProvider();
        if (isp   != null && !isp.isBlank())   sb.append(" · ISP: ").append(isp);
        
        String typ = saved.getTypeOfConnection();
        if (typ   != null && !typ.isBlank())   sb.append(" · Type: ").append(typ);
        
        String spd = saved.getSpeed();
        if (spd   != null && !spd.isBlank())   sb.append(" · Speed: ").append(spd);
        
        String staticIp = saved.getStaticIpAddress();
        if (staticIp != null && !staticIp.isBlank()) sb.append(" · IP: ").append(staticIp);
        
        if (!Boolean.TRUE.equals(saved.getIsConnected())) sb.append(" · Status: Inactive");
        
        // Plan & Billing fields
        String pln = strVal(req.get("planName"));
        if (pln   != null)                     sb.append(" · Plan: ").append(pln);
        
        String price = strVal(req.get("planPrice"));
        if (price != null)                     sb.append(" · Price: ").append(price);
        
        String acct = strVal(req.get("accountNumber"));
        if (acct  != null)                     sb.append(" · Account: ").append(acct);
        
        String contract = strVal(req.get("planContract"));
        if (contract != null)                  sb.append(" · Contract: ").append(contract);
        
        // Boolean flags
        if (parseBool(req.get("isWired"), false))    sb.append(" · Wired");
        if (parseBool(req.get("isWireless"), false)) sb.append(" · Wireless");
        if (parseBool(req.get("isShared"), false))   sb.append(" · Shared");
        if (parseBool(req.get("isFree"), false))      sb.append(" · Free");
        
        return sb.toString();
    }

    private String actor(Authentication auth) {
        return (auth != null && auth.getName() != null) ? auth.getName() : "unknown";
    }

    private List<Area> getVisibleAreasForUser(Authentication auth) {
        List<Area> allAreas = locationService.getAllAreas();
        if (auth == null || auth.getName() == null) return allAreas;

        User currentUser = userRepository.findByEmail(auth.getName()).orElse(null);
        if (currentUser == null) return allAreas;

        Integer roleId = currentUser.getRole();
        Integer areaId = currentUser.getAreaId();

        // Admin (1) and SRD Operation (4) can access all areas.
        if (roleId != null && (roleId == 1 || roleId == 4 || roleId == 5)) return allAreas;

        // Area Admin (2) and User (3) only see their assigned area.
        if (areaId == null) return List.of();
        return allAreas.stream()
                .filter(a -> areaId.equals(a.getId()))
                .collect(Collectors.toList());
    }

    private ResponseEntity<Map<String, Object>> err(String msg) {
        Map<String, Object> e = new HashMap<>();
        e.put("success", false); e.put("message", msg);
        return ResponseEntity.status(500).body(e);
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) { try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return null; } }
        if (value instanceof Number)  return ((Number) value).intValue();
        return null;
    }

    private String strVal(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private PostalOffice buildPostalOfficeFromRequest(Map<String, Object> requestData) {
        PostalOffice office = new PostalOffice();

        // Basic Info
        if (requestData.get("name") != null) {
            String officeName = strVal(requestData.get("name"));
            if (officeName != null) office.setName(officeName);
        }
        if (requestData.get("postmaster")             != null) office.setPostmaster(requestData.get("postmaster").toString());
        if (requestData.get("address")                != null) office.setAddress(requestData.get("address").toString());
        if (requestData.get("zipCode")                != null) office.setZipCode(requestData.get("zipCode").toString());

        // Classification & Services
        if (requestData.get("classification")         != null) office.setClassification(requestData.get("classification").toString());
        if (requestData.get("serviceProvided")        != null) office.setServiceProvided(requestData.get("serviceProvided").toString());
        if (requestData.get("remarks")                != null) office.setRemarks(requestData.get("remarks").toString());

        // Office Status (OPEN / CLOSED)
        if (requestData.get("officeStatus")           != null) office.setOfficeStatus(requestData.get("officeStatus").toString());

        // Connectivity / ISP
        if (requestData.get("internetServiceProvider") != null) office.setInternetServiceProvider(requestData.get("internetServiceProvider").toString());
        if (requestData.get("typeOfConnection")       != null) office.setTypeOfConnection(requestData.get("typeOfConnection").toString());
        if (requestData.get("speed")                  != null) office.setSpeed(requestData.get("speed").toString());
        if (requestData.get("staticIpAddress")        != null) office.setStaticIpAddress(requestData.get("staticIpAddress").toString());

        // ISP Contact
        if (requestData.get("ispContactPerson")       != null) office.setIspContactPerson(requestData.get("ispContactPerson").toString());
        if (requestData.get("ispContactNumber")       != null) office.setIspContactNumber(requestData.get("ispContactNumber").toString());

        // Postal Office Contact (Step 4)
        if (requestData.get("postalOfficeContactPerson") != null) office.setPostalOfficeContactPerson(requestData.get("postalOfficeContactPerson").toString());
        if (requestData.get("postalOfficeContactNumber") != null) office.setPostalOfficeContactNumber(requestData.get("postalOfficeContactNumber").toString());

        // Postmaster Contact Number — stored as postal office contact number if no dedicated column
        // (postalOfficeContactNumber from Step 4 takes priority if also filled)
        if (requestData.get("postmasterContactNumber") != null && office.getPostalOfficeContactNumber() == null)
            office.setPostalOfficeContactNumber(requestData.get("postmasterContactNumber").toString());

        // Staff
        if (requestData.get("noOfEmployees")          != null) office.setNoOfEmployees(parseInteger(requestData.get("noOfEmployees")));
        if (requestData.get("noOfPostalTellers")      != null) office.setNoOfPostalTellers(parseInteger(requestData.get("noOfPostalTellers")));
        if (requestData.get("noOfLetterCarriers")     != null) office.setNoOfLetterCarriers(parseInteger(requestData.get("noOfLetterCarriers")));

        // Location Hierarchy
        if (requestData.get("areaId") != null) {
            Integer areaId = parseInteger(requestData.get("areaId"));
            if (areaId != null) locationService.getAllAreas().stream().filter(a -> a.getId().equals(areaId)).findFirst().ifPresent(office::setArea);
        }
        if (requestData.get("provinceId") != null) {
            Integer provinceId = parseInteger(requestData.get("provinceId"));
            Integer areaId     = parseInteger(requestData.get("areaId"));
            if (provinceId != null && areaId != null) {
                locationService.getProvincesByArea(areaId).stream()
                        .filter(p -> p.getId().equals(provinceId))
                        .findFirst()
                        .ifPresent(p -> {
                            office.setProvince(p);
                            if (p.getRegions() != null) {
                                office.setRegion(p.getRegions());
                            }
                        });
            }
        }
        if (requestData.get("cityMunId") != null) {
            Integer cityMunId  = parseInteger(requestData.get("cityMunId"));
            Integer provinceId = parseInteger(requestData.get("provinceId"));
            if (cityMunId != null && provinceId != null)
                locationService.getCitiesByProvince(provinceId).stream().filter(c -> c.getId().equals(cityMunId)).findFirst().ifPresent(office::setCityMunicipality);
        }
        if (requestData.get("barangayId") != null) {
            Integer barangayId = parseInteger(requestData.get("barangayId"));
            Integer cityMunId  = parseInteger(requestData.get("cityMunId"));
            if (barangayId != null && cityMunId != null)
                locationService.getBarangaysByCity(cityMunId).stream().filter(b -> b.getId().equals(barangayId)).findFirst().ifPresent(office::setBarangay);
        }

        // Coordinates
        if (requestData.get("latitude") != null) {
            Object v = requestData.get("latitude");
            try { if (v instanceof Number) office.setLatitude(((Number) v).doubleValue()); else if (v instanceof String && !((String)v).isEmpty()) office.setLatitude(Double.parseDouble((String) v)); } catch (NumberFormatException ignored) {}
        }
        if (requestData.get("longitude") != null) {
            Object v = requestData.get("longitude");
            try { if (v instanceof Number) office.setLongitude(((Number) v).doubleValue()); else if (v instanceof String && !((String)v).isEmpty()) office.setLongitude(Double.parseDouble((String) v)); } catch (NumberFormatException ignored) {}
        }

        // Connection Status
        Object statusVal = requestData.get("connectionStatus");
        if (statusVal instanceof Boolean) office.setIsConnected((Boolean) statusVal);
        else if (statusVal instanceof String) office.setIsConnected(Boolean.parseBoolean((String) statusVal));
        else office.setIsConnected(false);

        return office;
    }
}