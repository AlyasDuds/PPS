package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.*;
import com.pps.profilesystem.DTO.ConnectivityNotification;
import com.pps.profilesystem.Service.ConnectivityNotificationService;
import com.pps.profilesystem.Service.ApprovalService;
import com.pps.profilesystem.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REST endpoints used by edit-modal.js
 *
 *  GET  /api/postal-office/{id}  ->  fetch office data for the modal
 *  PUT  /api/postal-office/{id}  ->  save changes from the modal
 */
@RestController
@RequestMapping("/api/postal-office")
public class PostalOfficeEditRestController {

    @Autowired private PostalOfficeRepository         postalOfficeRepository;
    @Autowired private ConnectivityNotificationService notifService;
    @Autowired private ApprovalService                approvalService;
    @Autowired private AreaRepository                 areaRepository;
    @Autowired private RegionsRepository              regionsRepository;
    @Autowired private ProvinceRepository             provinceRepository;
    @Autowired private CityMunicipalityRepository     cityMunicipalityRepository;
    @Autowired private BarangayRepository             barangayRepository;
    @Autowired private ConnectivityRepository         connectivityRepository;
    @Autowired private ProviderRepository             providerRepository;

    // -- GET --
    
    @GetMapping("/by-area/{areaId}")
    public ResponseEntity<?> getOfficesByArea(@PathVariable Integer areaId) {
        try {
            List<PostalOffice> offices = postalOfficeRepository.findByAreaId(areaId);
            List<Map<String, Object>> officeList = new ArrayList<>();
            
            for (PostalOffice office : offices) {
                Map<String, Object> officeMap = new HashMap<>();
                officeMap.put("id", office.getId());
                officeMap.put("name", office.getName());
                officeList.add(officeMap);
            }
            
            return ResponseEntity.ok(officeList);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to load offices: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getOffice(@PathVariable Integer id) {
        try {
            return postalOfficeRepository.findByIdWithAllAssociations(id)
                .<ResponseEntity<?>>map(o -> {
                    Map<String, Object> d = new LinkedHashMap<>();

                    // Basic
                    d.put("id",                         o.getId());
                    d.put("name",                       o.getName());
                    d.put("postmaster",                 o.getPostmaster());
                    d.put("classification",             o.getClassification());
                    d.put("serviceProvided",            o.getServiceProvided());

                    // Address / coordinates
                    d.put("address",                    o.getAddress());
                    d.put("zipCode",                    o.getZipCode());
                    d.put("latitude",                   o.getLatitude());
                    d.put("longitude",                  o.getLongitude());

                    // Location hierarchy IDs -- JS uses these to pre-select dropdowns
                    // Area is EAGER -- safe to access directly
                    d.put("areaId", o.getArea() != null ? o.getArea().getId() : null);

                    // LAZY associations -- wrapped in try-catch to avoid LazyInitializationException
                    try { d.put("regionId",   o.getRegion()           != null ? o.getRegion().getId()           : null); }
                    catch (Exception e) { d.put("regionId",   null); }
                    try { d.put("provinceId", o.getProvince()         != null ? o.getProvince().getId()         : null); }
                    catch (Exception e) { d.put("provinceId", null); }
                    try { d.put("cityMunId",  o.getCityMunicipality() != null ? o.getCityMunicipality().getId() : null); }
                    catch (Exception e) { d.put("cityMunId",  null); }
                    try { d.put("barangayId", o.getBarangay()         != null ? o.getBarangay().getId()         : null); }
                    catch (Exception e) { d.put("barangayId", null); }

                    // Connectivity
                    d.put("connectionStatus", o.getIsConnected());
                    d.put("officeStatus", o.getOfficeStatus());

                    // Fetch latest connectivity record to populate dates and Plan & Billing fields
                    // Priority: Active record (not disconnected) > Latest record
                    int currentYear = LocalDateTime.now().getYear();

                    connectivityRepository.findByPostalOfficeIdAndDateDisconnectedIsNull(o.getId())
                    .or(() ->
                        connectivityRepository.findTopByPostalOfficeIdOrderByDateConnectedDesc(o.getId())
                    )
                    .ifPresent(conn -> {

                            int connYear = conn.getDateConnected() != null
                                    ? conn.getDateConnected().getYear()
                                    : 0;

                            String quarter = conn.getDateConnected() != null
                                    ? getCurrentQuarter(conn.getDateConnected().getMonthValue())
                                    : "";

                            String status;
                            boolean isActive = conn.getDateDisconnected() == null;

                            if (isActive) {
                                // Active record - show current year/quarter, not connection year
                                String currentQuarter = getCurrentQuarter(LocalDateTime.now().getMonthValue());
                                status = currentYear + " " + currentQuarter + " (ACTIVE)";
                            } else if (connYear < currentYear) {
                                // Historical disconnected record
                                status = connYear + " " + quarter + " (HISTORICAL)";
                            } else {
                                // Current year but disconnected
                                status = connYear + " " + quarter + " (DISCONNECTED)";
                            }

                            d.put("connectivityRecordLabel", status);

                            d.put("dateConnected",
                                conn.getDateConnected() != null
                                ? conn.getDateConnected().toLocalDate().toString()
                                : null);

                            d.put("dateDisconnected",
                                conn.getDateDisconnected() != null
                                ? conn.getDateDisconnected().toLocalDate().toString()
                                : null);


                            // Plan & Billing
                            d.put("planName", conn.getPlanName());

                            d.put("planPrice",
                                conn.getPlanPrice() != null
                                ? conn.getPlanPrice().toString()
                                : null);

                            d.put("accountNumber", conn.getAccountNumber());
                            d.put("planContract", conn.getPlanContract());

                            d.put("isWired", conn.getIsWired());
                            d.put("isWireless", conn.getIsWireless());
                            d.put("isShared", conn.getIsShared());
                            d.put("isFree", conn.getIsFree());
                        });
                    d.put("internetServiceProvider",    o.getInternetServiceProvider());
                    d.put("typeOfConnection",           o.getTypeOfConnection());
                    d.put("speed",                      o.getSpeed());
                    d.put("staticIpAddress",            o.getStaticIpAddress());

                    // Staff
                    d.put("noOfEmployees",              o.getNoOfEmployees());
                    d.put("noOfPostalTellers",          o.getNoOfPostalTellers());
                    d.put("noOfLetterCarriers",         o.getNoOfLetterCarriers());

                    // Contacts
                    d.put("postalOfficeContactPerson",  o.getPostalOfficeContactPerson());
                    d.put("postalOfficeContactNumber",  o.getPostalOfficeContactNumber());
                    d.put("ispContactPerson",           o.getIspContactPerson());
                    d.put("ispContactNumber",           o.getIspContactNumber());
                    d.put("remarks",                    o.getRemarks());

                    // Cover photo URL for popup
                    if (o.getCoverPhoto() != null && !o.getCoverPhoto().isBlank()) {
                        d.put("coverPhotoUrl", "/api/postal-office/" + id + "/cover-photo/1");
                    } else {
                        d.put("coverPhotoUrl", null);
                    }

                    return ResponseEntity.ok(d);
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            System.err.println("[PostalOfficeEditRestController] GET /" + id + " ERROR: " + e.getMessage());
            e.printStackTrace();
            return error(500, "Failed to load office data: " + e.getMessage());
        }
    }

    // -- PUT --

    @PutMapping("/{id}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "connectivityStats", allEntries = true)
    public ResponseEntity<?> updateOffice(@PathVariable Integer id,
                                          @RequestBody Map<String, Object> body,
                                          Authentication auth) {
        Optional<PostalOffice> opt = postalOfficeRepository.findById(id);
        if (opt.isEmpty()) return error(404, "Office not found with ID: " + id);

        try {
            PostalOffice o = opt.get();
            Snapshot before = Snapshot.of(o);
            String actor = actor(auth);
            System.err.println("[PostalOfficeEditRestController] Starting update for office ID: " + id + ", current status: " + o.getIsConnected());

            boolean isSystemAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                                || a.getAuthority().equals("ROLE_SRD_OPERATION"));
            boolean isAreaAdmin = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_AREA_ADMIN"));

            // Validate: Prevent editing connectivity for completed quarters
            // Historical years (2025) and completed quarters in current year should be locked
            int currentYear = java.time.LocalDateTime.now().getYear();
            int currentMonth = java.time.LocalDateTime.now().getMonthValue();
            String currentQuarter = getCurrentQuarter(currentMonth);
            
            Optional<Connectivity> latestConnOpt = connectivityRepository
                    .findTopByPostalOfficeIdOrderByDateConnectedDesc(o.getId());

            if (latestConnOpt.isPresent()) {
                Connectivity conn = latestConnOpt.get();
                if (conn.getDateConnected() != null) {
                    int connYear = conn.getDateConnected().getYear();
<<<<<<< HEAD
                    String connQuarter = getCurrentQuarter(conn.getDateConnected().getMonthValue());
=======
>>>>>>> 01160e722ab6061d2726b3a6b904d700408477a5

                    // System admin can edit historical data - skip validation for system admin
                    if (!isSystemAdmin) {
                        // Block editing connectivity dates for 2024 and 2025 (true data)
                        // Allow connectionStatus changes - will create new record with today's date
                        if (connYear == 2024 || connYear == 2025) {
                            // Only block if user is actually trying to change the dates
                            // Empty or null values should be ignored (allow connectionStatus toggle)
                            if (body.containsKey("dateConnected")) {
                                Object val = body.get("dateConnected");
                                if (val != null && !String.valueOf(val).trim().isEmpty()) {
                                    try {
                                        String dConnStr = val.toString().trim();
                                        LocalDateTime newDate = java.time.LocalDate.parse(dConnStr).atStartOfDay();
                                        // Only block if trying to change to a different date
                                        if (!newDate.toLocalDate().equals(conn.getDateConnected().toLocalDate())) {
                                            System.err.println("[PostalOfficeEditRestController] Blocking dateConnected change for " + connYear + " record");
                                            return error(400, "Cannot modify connectivity dates for records from "
                                                    + connYear + ". Only System Admin can edit.");
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                            if (body.containsKey("dateDisconnected")) {
                                Object val = body.get("dateDisconnected");
                                if (val != null && !String.valueOf(val).trim().isEmpty()) {
                                    try {
                                        String dDisStr = val.toString().trim();
                                        LocalDateTime newDate = java.time.LocalDate.parse(dDisStr).atTime(23, 59, 59);
                                        // Only block if trying to change to a different date
                                        if (conn.getDateDisconnected() == null
                                                || !newDate.toLocalDate().equals(conn.getDateDisconnected().toLocalDate())) {
                                            System.err.println("[PostalOfficeEditRestController] Blocking dateDisconnected change for " + connYear + " record");
                                            return error(400, "Cannot modify connectivity dates for records from "
                                                    + connYear + ". Only System Admin can edit.");
                                        }
                                    } catch (Exception ignored) {}
<<<<<<< HEAD
                                }
                            }
                        }
                        // Block editing completed quarters in current year
                        // Q1 locked when in Q2, Q3, or Q4
                        // Q2 locked when in Q3 or Q4
                        // Q3 locked when in Q4
                        else if (connYear == currentYear) {
                            boolean isQuarterLocked = false;
                            if ("Q1".equals(connQuarter) && ("Q2".equals(currentQuarter) || "Q3".equals(currentQuarter) || "Q4".equals(currentQuarter))) {
                                isQuarterLocked = true;
                            } else if ("Q2".equals(connQuarter) && ("Q3".equals(currentQuarter) || "Q4".equals(currentQuarter))) {
                                isQuarterLocked = true;
                            } else if ("Q3".equals(connQuarter) && "Q4".equals(currentQuarter)) {
                                isQuarterLocked = true;
                            }

                            if (isQuarterLocked) {
                                if (body.containsKey("dateConnected")) {
                                    Object val = body.get("dateConnected");
                                    if (val != null && !String.valueOf(val).trim().isEmpty()) {
                                        try {
                                            String dConnStr = val.toString().trim();
                                            LocalDateTime newDate = java.time.LocalDate.parse(dConnStr).atStartOfDay();
                                            if (!newDate.toLocalDate().equals(conn.getDateConnected().toLocalDate())) {
                                                System.err.println("[PostalOfficeEditRestController] Blocking dateConnected change for completed " + connQuarter + " record");
                                                return error(400, "Cannot modify connectivity dates for completed " + connQuarter + " records. Only System Admin can edit.");
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                                if (body.containsKey("dateDisconnected")) {
                                    Object val = body.get("dateDisconnected");
                                    if (val != null && !String.valueOf(val).trim().isEmpty()) {
                                        try {
                                            String dDisStr = val.toString().trim();
                                            LocalDateTime newDate = java.time.LocalDate.parse(dDisStr).atTime(23, 59, 59);
                                            if (conn.getDateDisconnected() == null
                                                    || !newDate.toLocalDate().equals(conn.getDateDisconnected().toLocalDate())) {
                                                System.err.println("[PostalOfficeEditRestController] Blocking dateDisconnected change for completed " + connQuarter + " record");
                                                return error(400, "Cannot modify connectivity dates for completed " + connQuarter + " records. Only System Admin can edit.");
                                            }
                                        } catch (Exception ignored) {}
                                    }
=======
>>>>>>> 01160e722ab6061d2726b3a6b904d700408477a5
                                }
                            }
                        }
                    }
                }
            }

            // Prepare changes for approval request if needed
            Map<String, Object> oldValues = createOldValuesMap(before);
            Map<String, Object> newValues = createNewValuesMap(body);

            if (isSystemAdmin) {
                // System admin can directly update
                Boolean oldStatus = o.getIsConnected();
                applyChanges(o, body);
                Boolean newStatus = o.getIsConnected();

                handleConnectivityStatusChange(o, oldStatus, newStatus, auth, latestConnOpt);

                // System admin can edit dates of the latest connectivity record (only for current year)
                if (body.containsKey("dateConnected") || body.containsKey("dateDisconnected")) {
                    connectivityRepository.findTopByPostalOfficeIdOrderByDateConnectedDesc(o.getId())
                        .ifPresent(conn -> {
                            // Skip editing if this is a previous year record (already validated above, but double-check)
                            if (conn.getDateConnected() != null && conn.getDateConnected().getYear() < currentYear) {
                                return;
                            }

                            boolean changed = false;

                            if (body.containsKey("dateConnected")) {
                                Object val = body.get("dateConnected");
                                if (val == null || String.valueOf(val).trim().isEmpty()) {
                                    if (conn.getDateConnected() != null) {
                                        conn.setDateConnected(null);
                                        changed = true;
                                    }
                                } else {
                                    try {
                                        String dConnStr = val.toString().trim();
                                        LocalDateTime dConn = java.time.LocalDate.parse(dConnStr).atStartOfDay();
                                        if (conn.getDateConnected() == null || !conn.getDateConnected().toLocalDate().equals(dConn.toLocalDate())) {
                                            conn.setDateConnected(dConn);
                                            changed = true;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }

                            if (body.containsKey("dateDisconnected")) {
                                Object val = body.get("dateDisconnected");
                                if (val == null || String.valueOf(val).trim().isEmpty()) {
                                    if (conn.getDateDisconnected() != null) {
                                        conn.setDateDisconnected(null);
                                        changed = true;
                                    }
                                } else {
                                    try {
                                        String dDisStr = val.toString().trim();
                                        LocalDateTime dDis = java.time.LocalDate.parse(dDisStr).atTime(23, 59, 59);
                                        if (conn.getDateDisconnected() == null || !conn.getDateDisconnected().toLocalDate().equals(dDis.toLocalDate())) {
                                            conn.setDateDisconnected(dDis);
                                            changed = true;
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }

                            if (changed) {
                                connectivityRepository.save(conn);
                            }
                        });
                }

                // Save Plan & Billing fields to Connectivity record
                saveConnectivityPlanFields(o, body);

                postalOfficeRepository.save(o);

                // Diff + notify
                List<String> changes = diff(before, o);
                if (!changes.isEmpty()) {
                    ConnectivityNotification.Type type = resolveType(before, o);
                    Integer actorRoleId = ConnectivityNotificationService.roleIdFromAuthorities(
                            auth != null ? auth.getAuthorities() : null
                    );
                    Integer areaId = o.getArea() != null ? o.getArea().getId() : null;
                    notifService.pushAudit(
                            type,
                            o.getName(),
                            o.getId(),
                            actor,
                            actorRoleId,
                            String.join(" · ", changes),
                            null,
                            "CONNECTIVITY",
                            "PostalOffice",
                            o.getId() != null ? o.getId().longValue() : null,
                            areaId
                    );
                }

                return ResponseEntity.ok(Map.of("success", true, "message", "Office updated successfully."));
            } else if (isAreaAdmin) {
                // Area Admin edits require SRD Operation final approval (skip Area Admin step)
                if (approvalService.hasPendingRequestForOffice(id)) {
                    return error(409, activeRequestBlockMessage(id));
                }

                Integer areaId = o.getArea() != null ? o.getArea().getId() : null;
                approvalService.createAreaApprovedRequest(
                        ApprovalRequest.RequestType.EDIT_OFFICE,
                        id,
                        o.getName(),
                        actor,
                        oldValues,
                        newValues,
                        areaId,
                        actor,
                        null
                );

                return ResponseEntity.ok(Map.of(
                        "success", true, 
                        "message", "Your changes have been forwarded to SRD Operation for final approval.",
                        "requiresApproval", true
                ));
            } else {
                // Regular user needs Area Admin approval first, then SRD Operation final approval
                if (approvalService.hasPendingRequestForOffice(id)) {
                    return error(409, activeRequestBlockMessage(id));
                }

                Integer areaId = o.getArea() != null ? o.getArea().getId() : null;
                approvalService.createApprovalRequest(
                        ApprovalRequest.RequestType.EDIT_OFFICE,
                        id,
                        o.getName(),
                        actor,
                        oldValues,
                        newValues,
                        areaId
                );

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Your changes have been submitted for approval.",
                        "requiresApproval", true
                ));
            }

        } catch (Exception e) {
            return error(500, "Update failed: " + e.getMessage());
        }
    }

    private Map<String, Object> createOldValuesMap(Snapshot before) {
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("name", before.name);
        oldValues.put("postmaster", before.postmaster);
        oldValues.put("classification", before.classification);
        oldValues.put("serviceProvided", before.serviceProvided);
        oldValues.put("address", before.address);
        oldValues.put("zipCode", before.zipCode);
        oldValues.put("connectionStatus", before.connectionStatus);
        oldValues.put("officeStatus", before.officeStatus);
        oldValues.put("internetServiceProvider", before.isp);
        oldValues.put("typeOfConnection", before.connType);
        oldValues.put("speed", before.speed);
        oldValues.put("staticIpAddress", before.staticIp);
        oldValues.put("noOfEmployees", before.employees);
        oldValues.put("noOfPostalTellers", before.tellers);
        oldValues.put("noOfLetterCarriers", before.carriers);
        oldValues.put("postalOfficeContactPerson", before.contactPerson);
        oldValues.put("postalOfficeContactNumber", before.contactNumber);
        oldValues.put("ispContactPerson", before.ispContactPerson);
        oldValues.put("ispContactNumber", before.ispContactNumber);
        oldValues.put("remarks", before.remarks);
        // Plan & Billing fields (from Connectivity)
        oldValues.put("planName", before.planName);
        oldValues.put("planPrice", before.planPrice);
        oldValues.put("accountNumber", before.accountNumber);
        oldValues.put("planContract", before.planContract);
        oldValues.put("isWired", before.isWired);
        oldValues.put("isWireless", before.isWireless);
        oldValues.put("isShared", before.isShared);
        oldValues.put("isFree", before.isFree);
        return oldValues;
    }

    private Map<String, Object> createNewValuesMap(Map<String, Object> body) {
        Map<String, Object> newValues = new HashMap<>();
        copyIfPresent(body, newValues, "name");
        copyIfPresent(body, newValues, "postmaster");
        copyIfPresent(body, newValues, "classification");
        copyIfPresent(body, newValues, "serviceProvided");
        copyIfPresent(body, newValues, "address");
        copyIfPresent(body, newValues, "zipCode");
        copyIfPresent(body, newValues, "connectionStatus");
        copyIfPresent(body, newValues, "officeStatus");
        copyIfPresent(body, newValues, "internetServiceProvider");
        copyIfPresent(body, newValues, "typeOfConnection");
        copyIfPresent(body, newValues, "speed");
        copyIfPresent(body, newValues, "staticIpAddress");
        copyIfPresent(body, newValues, "noOfEmployees");
        copyIfPresent(body, newValues, "noOfPostalTellers");
        copyIfPresent(body, newValues, "noOfLetterCarriers");
        copyIfPresent(body, newValues, "postalOfficeContactPerson");
        copyIfPresent(body, newValues, "postalOfficeContactNumber");
        copyIfPresent(body, newValues, "ispContactPerson");
        copyIfPresent(body, newValues, "ispContactNumber");
        copyIfPresent(body, newValues, "remarks");
        // Plan & Billing fields are handled separately through Connectivity, not in approval maps
        copyIfPresent(body, newValues, "areaId");
        copyIfPresent(body, newValues, "regionId");
        copyIfPresent(body, newValues, "provinceId");
        copyIfPresent(body, newValues, "cityMunId");
        copyIfPresent(body, newValues, "barangayId");
        return newValues;
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void applyChanges(PostalOffice o, Map<String, Object> body) {
        // Basic
        set(body, "name",           v -> o.setName(v.toString().trim()));
        set(body, "postmaster",     v -> o.setPostmaster(str(v)));
        set(body, "classification", v -> o.setClassification(str(v)));
        set(body, "serviceProvided",v -> o.setServiceProvided(str(v)));

        // Address / coordinates
        set(body, "address",    v -> o.setAddress(str(v)));
        set(body, "zipCode",    v -> o.setZipCode(str(v)));
        set(body, "latitude",   v -> { try { o.setLatitude(Double.parseDouble(v.toString()));  } catch (Exception ignored) {} });
        set(body, "longitude",  v -> { try { o.setLongitude(Double.parseDouble(v.toString())); } catch (Exception ignored) {} });

        // Location hierarchy
        set(body, "areaId",     v -> { Integer x = num(v); if (x != null) areaRepository.findById(x).ifPresent(o::setArea); });
        set(body, "regionId",   v -> { Integer x = num(v); if (x != null) regionsRepository.findById(x).ifPresent(o::setRegion); });
        set(body, "provinceId", v -> { Integer x = num(v); if (x != null) provinceRepository.findById(x).ifPresent(o::setProvince); });
        set(body, "cityMunId",  v -> { Integer x = num(v); if (x != null) cityMunicipalityRepository.findById(x).ifPresent(o::setCityMunicipality); });
        set(body, "barangayId", v -> { Integer x = num(v); if (x != null) barangayRepository.findById(x).ifPresent(o::setBarangay); });

        // Connectivity
        set(body, "connectionStatus",         v -> o.setIsConnected(bool(v)));
        set(body, "officeStatus",             v -> o.setOfficeStatus(str(v)));
        set(body, "internetServiceProvider",  v -> o.setInternetServiceProvider(str(v)));
        set(body, "typeOfConnection",         v -> o.setTypeOfConnection(str(v)));
        set(body, "speed",                    v -> o.setSpeed(str(v)));
        set(body, "staticIpAddress",          v -> o.setStaticIpAddress(str(v)));

        // Staff
        set(body, "noOfEmployees",     v -> o.setNoOfEmployees(num(v)));
        set(body, "noOfPostalTellers", v -> o.setNoOfPostalTellers(num(v)));
        set(body, "noOfLetterCarriers",v -> o.setNoOfLetterCarriers(num(v)));

        // Contacts
        set(body, "postalOfficeContactPerson", v -> o.setPostalOfficeContactPerson(str(v)));
        set(body, "postalOfficeContactNumber", v -> o.setPostalOfficeContactNumber(str(v)));
        set(body, "ispContactPerson",          v -> o.setIspContactPerson(str(v)));
        set(body, "ispContactNumber",          v -> o.setIspContactNumber(str(v)));
        set(body, "remarks",                   v -> o.setRemarks(str(v)));
    }

    // -- Diff --

    private List<String> diff(Snapshot b, PostalOffice a) {
        List<String> lines = new ArrayList<>();
        cmp(lines, "Name",           b.name,            a.getName());
        cmp(lines, "Postmaster",     b.postmaster,       a.getPostmaster());
        cmp(lines, "Classification", b.classification,   a.getClassification());
        cmp(lines, "Services",       b.serviceProvided,  a.getServiceProvided());
        cmp(lines, "Address",        b.address,          a.getAddress());
        cmp(lines, "Zip Code",       b.zipCode,          a.getZipCode());
        if (!Objects.equals(b.connectionStatus, a.getIsConnected()))
            lines.add("Status: " + label(b.connectionStatus) + " -> " + label(a.getIsConnected()));
        cmp(lines, "ISP",        b.isp,      a.getInternetServiceProvider());
        cmp(lines, "Conn. Type", b.connType, a.getTypeOfConnection());
        cmp(lines, "Speed",      b.speed,    a.getSpeed());
        cmp(lines, "Static IP",  b.staticIp, a.getStaticIpAddress());
        cmpNum(lines, "Employees", b.employees, a.getNoOfEmployees());
        cmpNum(lines, "Tellers",   b.tellers,   a.getNoOfPostalTellers());
        cmpNum(lines, "Carriers",  b.carriers,  a.getNoOfLetterCarriers());
        cmp(lines, "Office Contact", b.contactPerson,    a.getPostalOfficeContactPerson());
        cmp(lines, "Office #",       b.contactNumber,    a.getPostalOfficeContactNumber());
        cmp(lines, "ISP Contact",    b.ispContactPerson, a.getIspContactPerson());
        cmp(lines, "ISP #",          b.ispContactNumber, a.getIspContactNumber());
        cmp(lines, "Remarks",        b.remarks,          a.getRemarks());
        
        // Plan & Billing fields (from Connectivity) - compare with current connectivity
        Connectivity currentConn = a.getActiveConnectivity();
        if (currentConn != null) {
            cmp(lines, "Plan Name",     b.planName,     currentConn.getPlanName());
            cmp(lines, "Plan Price",    b.planPrice,    currentConn.getPlanPrice() != null ? currentConn.getPlanPrice().toString() : null);
            cmp(lines, "Account #",     b.accountNumber, currentConn.getAccountNumber());
            cmp(lines, "Contract",      b.planContract, currentConn.getPlanContract());
            cmpBool(lines, "Wired",      b.isWired,      currentConn.getIsWired());
            cmpBool(lines, "Wireless",   b.isWireless,   currentConn.getIsWireless());
            cmpBool(lines, "Shared",     b.isShared,     currentConn.getIsShared());
            cmpBool(lines, "Free",       b.isFree,       currentConn.getIsFree());
        }
        
        return lines;
    }

    private void cmp(List<String> out, String lbl, String b, String a) {
        if (!blank(b).equals(blank(a))) out.add(lbl + ": " + blank(b) + " -> " + blank(a));
    }
    private void cmpNum(List<String> out, String lbl, Integer b, Integer a) {
        String bv = b == null ? "?" : String.valueOf(b);
        String av = a == null ? "?" : String.valueOf(a);
        if (!bv.equals(av)) out.add(lbl + ": " + bv + " -> " + av);
    }
    private void cmpBool(List<String> out, String lbl, Boolean b, Boolean a) {
        String bv = b == null ? "?" : (b ? "Yes" : "No");
        String av = a == null ? "?" : (a ? "Yes" : "No");
        if (!bv.equals(av)) out.add(lbl + ": " + bv + " -> " + av);
    }
    private String blank(String s) { return (s == null || s.isBlank()) ? "?" : s.trim(); }
    private String label(Boolean b){ return Boolean.TRUE.equals(b) ? "Active" : "Inactive"; }

    private ConnectivityNotification.Type resolveType(Snapshot b, PostalOffice a) {
        if (!Objects.equals(b.connectionStatus, a.getIsConnected()))
            return Boolean.TRUE.equals(a.getIsConnected())
                   ? ConnectivityNotification.Type.CONNECTED
                   : ConnectivityNotification.Type.DISCONNECTED;
        return ConnectivityNotification.Type.UPDATED;
    }

    // -- Snapshot --

    private static class Snapshot {
        String  name, postmaster, classification, serviceProvided, address, zipCode, officeStatus;
        Boolean connectionStatus;
        String  isp, connType, speed, staticIp;
        Integer employees, tellers, carriers;
        String  contactPerson, contactNumber, ispContactPerson, ispContactNumber, remarks;
        // Plan & Billing fields (from Connectivity)
        String  planName, planPrice, accountNumber, planContract;
        Boolean isWired, isWireless, isShared, isFree;

        static Snapshot of(PostalOffice o) {
            Snapshot s = new Snapshot();
            s.name             = o.getName();
            s.postmaster       = o.getPostmaster();
            s.classification   = o.getClassification();
            s.serviceProvided  = o.getServiceProvided();
            s.address          = o.getAddress();
            s.zipCode          = o.getZipCode();
            s.officeStatus     = o.getOfficeStatus();
            s.connectionStatus = o.getIsConnected();
            s.isp              = o.getInternetServiceProvider();
            s.connType         = o.getTypeOfConnection();
            s.speed            = o.getSpeed();
            s.staticIp         = o.getStaticIpAddress();
            s.employees        = o.getNoOfEmployees();
            s.tellers          = o.getNoOfPostalTellers();
            s.carriers         = o.getNoOfLetterCarriers();
            s.contactPerson    = o.getPostalOfficeContactPerson();
            s.contactNumber    = o.getPostalOfficeContactNumber();
            s.ispContactPerson = o.getIspContactPerson();
            s.ispContactNumber = o.getIspContactNumber();
            s.remarks          = o.getRemarks();
            // Plan & Billing fields from Connectivity
            if (o.getActiveConnectivity() != null) {
                Connectivity c = o.getActiveConnectivity();
                s.planName      = c.getPlanName();
                s.planPrice     = c.getPlanPrice() != null ? c.getPlanPrice().toString() : null;
                s.accountNumber = c.getAccountNumber();
                s.planContract  = c.getPlanContract();
                s.isWired       = c.getIsWired();
                s.isWireless    = c.getIsWireless();
                s.isShared      = c.getIsShared();
                s.isFree        = c.getIsFree();
            }
            return s;
        }
    }

    // -- Generic helpers --

    private String actor(Authentication auth) {
        return (auth != null && auth.getName() != null) ? auth.getName() : "unknown";
    }
    private void set(Map<String, Object> body, String key, java.util.function.Consumer<Object> setter) {
        if (body.containsKey(key)) setter.accept(body.get(key));
    }
    private String  str(Object v)  { return v == null ? null : v.toString().trim(); }
    private Boolean bool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        return Boolean.parseBoolean(v == null ? "false" : v.toString());
    }
    private Integer num(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
    private ResponseEntity<?> error(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", msg));
    }

    private void handleConnectivityStatusChange(PostalOffice office, Boolean oldStatus, Boolean newStatus, Authentication auth, Optional<Connectivity> latestBeforeChange) {
        int currentYear = LocalDateTime.now().getYear();
        int currentMonth = LocalDateTime.now().getMonthValue();
        String currentQuarter = getCurrentQuarter(currentMonth);
        
        boolean isSystemAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                            || a.getAuthority().equals("ROLE_SRD_OPERATION"));

        if (!Boolean.TRUE.equals(oldStatus) && Boolean.TRUE.equals(newStatus)) {
            // ── inactive → active ──────────────────────────────────────────────

<<<<<<< HEAD
            // Close any existing open record first (safety net)
            Connectivity existingOpen = connectivityRepository
                    .findByPostalOfficeIdAndDateDisconnectedIsNull(office.getId())
                    .orElse(null);
            
            if (existingOpen != null) {
                existingOpen.setDateDisconnected(LocalDateTime.now());
                connectivityRepository.save(existingOpen);
            }

            // Use the already-fetched latest record (before any changes)
            // to avoid picking up the just-closed record
            Connectivity newConn = new Connectivity();
            newConn.setPostalOffice(office);
            newConn.setDateConnected(LocalDateTime.now());
            newConn.setDateDisconnected(null);

            if (latestBeforeChange.isPresent()) {
                Connectivity prev = latestBeforeChange.get();
                newConn.setProvider(prev.getProvider());
                newConn.setPlanName(prev.getPlanName());
                newConn.setPlanPrice(prev.getPlanPrice());
                newConn.setAccountNumber(prev.getAccountNumber());
                newConn.setPlanContract(prev.getPlanContract());
                newConn.setIsWired(prev.getIsWired());
                newConn.setIsWireless(prev.getIsWireless());
                newConn.setIsShared(prev.getIsShared());
                newConn.setIsFree(prev.getIsFree());
            } else {
                Provider defaultProvider = providerRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Provider p = new Provider();
                        p.setName("Default Provider");
                        return providerRepository.save(p);
                    });
                newConn.setProvider(defaultProvider);
            }

            Connectivity saved = connectivityRepository.save(newConn);
            office.setActiveConnectivity(saved);
=======
            // Unique constraint on OfficeID in Connectivity table
            // Cannot insert new record -- reuse existing record
            if (latestBeforeChange.isPresent()) {
                // Reopen existing record -- new dateConnected (current year), clear dateDisconnected
                Connectivity conn = latestBeforeChange.get();
                conn.setDateConnected(LocalDateTime.now());
                conn.setDateDisconnected(null);
                Connectivity saved = connectivityRepository.save(conn);
                office.setActiveConnectivity(saved);
            } else {
                // No existing record -- create new one (first time)
                Provider defaultProvider = providerRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Provider p = new Provider();
                        p.setName("Default Provider");
                        return providerRepository.save(p);
                    });
                Connectivity newConn = new Connectivity();
                newConn.setPostalOffice(office);
                newConn.setProvider(defaultProvider);
                newConn.setDateConnected(LocalDateTime.now());
                newConn.setDateDisconnected(null);
                Connectivity saved = connectivityRepository.save(newConn);
                office.setActiveConnectivity(saved);
            }
>>>>>>> 01160e722ab6061d2726b3a6b904d700408477a5
            
        } else if (Boolean.TRUE.equals(oldStatus) && !Boolean.TRUE.equals(newStatus)) {
            // ── Switching to INACTIVE ────────────────────────────────────────────
            // Close the current open record
            
            Connectivity conn = connectivityRepository
                .findByPostalOfficeIdAndDateDisconnectedIsNull(office.getId())
                .orElse(null);

            if (conn != null) {
                // Close the record
                conn.setDateDisconnected(LocalDateTime.now());
                connectivityRepository.save(conn);
                office.setActiveConnectivity(null);
            }
        }
    }

    private Connectivity createConnectivityRecord(PostalOffice office) {
        Provider defaultProvider = providerRepository.findAll().stream()
            .findFirst()
            .orElseGet(() -> {
                Provider newProvider = new Provider();
                newProvider.setName("Default Provider");
                return providerRepository.save(newProvider);
            });
        
        Connectivity connectivity = new Connectivity();
        connectivity.setPostalOffice(office);
        connectivity.setProvider(defaultProvider);
        connectivity.setDateConnected(LocalDateTime.now());
        return connectivity;
    }

    private String activeRequestBlockMessage(Integer officeId) {
        Optional<ApprovalRequest> active = approvalService.getLatestActiveRequestForOffice(officeId);
        if (active.isEmpty()) {
            return "There is already a pending approval request for this office.";
        }

        ApprovalRequest req = active.get();
        if (req.getStatus() == ApprovalRequest.RequestStatus.AREA_APPROVED) {
            return "This office already has a request waiting for SRD Operation final approval.";
        }
        return "This office already has a pending request waiting for Area Admin review.";
    }

    private String getCurrentQuarter(int month) {
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }

    private void saveConnectivityPlanFields(PostalOffice office, Map<String, Object> body) {
        // Get or create the active connectivity record
        Connectivity conn = office.getActiveConnectivity();
        if (conn == null) {
            // If no active connectivity, try to find the latest one
            Optional<Connectivity> latest = connectivityRepository.findTopByPostalOfficeIdOrderByDateConnectedDesc(office.getId());
            if (latest.isPresent()) {
                conn = latest.get();
            } else {
                // Create a new connectivity record if none exists
                Provider defaultProvider = providerRepository.findAll().stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Provider newProvider = new Provider();
                        newProvider.setName("Default Provider");
                        return providerRepository.save(newProvider);
                    });
                conn = new Connectivity();
                conn.setPostalOffice(office);
                conn.setProvider(defaultProvider);
                conn.setDateConnected(LocalDateTime.now());
            }
        }

        boolean changed = false;

        // Save Plan & Billing fields
        if (body.containsKey("planName")) {
            String val = str(body.get("planName"));
            if (!Objects.equals(val, conn.getPlanName())) {
                conn.setPlanName(val);
                changed = true;
            }
        }
        if (body.containsKey("planPrice")) {
            String val = str(body.get("planPrice"));
            if (val != null && !val.isBlank()) {
                try {
                    java.math.BigDecimal price = new java.math.BigDecimal(val);
                    if (!price.equals(conn.getPlanPrice())) {
                        conn.setPlanPrice(price);
                        changed = true;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (body.containsKey("accountNumber")) {
            String val = str(body.get("accountNumber"));
            if (!Objects.equals(val, conn.getAccountNumber())) {
                conn.setAccountNumber(val);
                changed = true;
            }
        }
        if (body.containsKey("planContract")) {
            String val = str(body.get("planContract"));
            if (!Objects.equals(val, conn.getPlanContract())) {
                conn.setPlanContract(val);
                changed = true;
            }
        }
        if (body.containsKey("isWired")) {
            Boolean val = bool(body.get("isWired"));
            if (!Objects.equals(val, conn.getIsWired())) {
                conn.setIsWired(val);
                changed = true;
            }
        }
        if (body.containsKey("isWireless")) {
            Boolean val = bool(body.get("isWireless"));
            if (!Objects.equals(val, conn.getIsWireless())) {
                conn.setIsWireless(val);
                changed = true;
            }
        }
        if (body.containsKey("isShared")) {
            Boolean val = bool(body.get("isShared"));
            if (!Objects.equals(val, conn.getIsShared())) {
                conn.setIsShared(val);
                changed = true;
            }
        }
        if (body.containsKey("isFree")) {
            Boolean val = bool(body.get("isFree"));
            if (!Objects.equals(val, conn.getIsFree())) {
                conn.setIsFree(val);
                changed = true;
            }
        }

        if (changed) {
            Connectivity saved = connectivityRepository.save(conn);
            office.setActiveConnectivity(saved);
        }
    }
}
