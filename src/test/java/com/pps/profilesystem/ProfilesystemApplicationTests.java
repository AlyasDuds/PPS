package com.pps.profilesystem;

import com.pps.profilesystem.Entity.*;
import com.pps.profilesystem.Repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.util.*;

@SpringBootTest
class ProfilesystemApplicationTests {

    @Autowired
    private PostalOfficeRepository poRepo;

    @Autowired
    private ConnectivityRepository connRepo;

    @Autowired
    private ArchivedOfficeRepository archRepo;

    @Test
    void contextLoads() {}

    @Test
    void runDiagnostics() {
        System.out.println("=== DIAGNOSTICS FOR AREA 1 ===");
        List<PostalOffice> allOffices = poRepo.findAll();
        List<PostalOffice> area1Offices = allOffices.stream()
            .filter(po -> po.getArea() != null && po.getArea().getId() == 1)
            .toList();

        System.out.println("Total offices in database under Area 1: " + area1Offices.size());
        
        long archivedCount = area1Offices.stream()
            .filter(po -> archRepo.existsByPostalOfficeId(po.getId()))
            .count();
        System.out.println("Archived offices in Area 1: " + archivedCount);

        List<PostalOffice> nonArchivedArea1 = area1Offices.stream()
            .filter(po -> !archRepo.existsByPostalOfficeId(po.getId()))
            .sorted(Comparator.comparing(PostalOffice::getId))
            .toList();
        System.out.println("Non-archived offices in Area 1: " + nonArchivedArea1.size());

        // Check active at end of Q4 2025 (2025-12-31 23:59:59)
        LocalDateTime qStart = LocalDateTime.of(2025, 10, 1, 0, 0, 0);
        LocalDateTime qEnd = LocalDateTime.of(2025, 12, 31, 23, 59, 59);

        List<Connectivity> activeAtQEnd = connRepo.findActiveAtDate(qEnd);
        Set<Integer> activeAtQEndIds = new HashSet<>();
        for (Connectivity c : activeAtQEnd) {
            if (c.getPostalOffice() != null && c.getPostalOffice().getArea() != null && c.getPostalOffice().getArea().getId() == 1) {
                activeAtQEndIds.add(c.getPostalOffice().getId());
            }
        }
        System.out.println("Distinct active connected offices at Q4 2025 end (Area 1): " + activeAtQEndIds.size());

        List<Connectivity> activeAtQStart = connRepo.findActiveAtDate(qStart);
        Set<Integer> activeAtQStartIds = new HashSet<>();
        for (Connectivity c : activeAtQStart) {
            if (c.getPostalOffice() != null && c.getPostalOffice().getArea() != null && c.getPostalOffice().getArea().getId() == 1) {
                activeAtQStartIds.add(c.getPostalOffice().getId());
            }
        }
        System.out.println("Distinct active connected offices at Q4 2025 start (Area 1): " + activeAtQStartIds.size());

        List<Connectivity> newlyConnected = connRepo.findByDateConnectedBetween(qStart, qEnd).stream()
            .filter(c -> c.getPostalOffice() != null && c.getPostalOffice().getArea() != null && c.getPostalOffice().getArea().getId() == 1)
            .toList();
        Set<Integer> newlyConnectedIds = new HashSet<>();
        for (Connectivity c : newlyConnected) {
            newlyConnectedIds.add(c.getPostalOffice().getId());
        }
        System.out.println("Distinct newly connected in Q4 2025 (Area 1): " + newlyConnectedIds.size());

        System.out.println("--- LIST OF ARCHIVED OFFICES IN AREA 1 ---");
        List<PostalOffice> archivedArea1 = area1Offices.stream()
            .filter(po -> archRepo.existsByPostalOfficeId(po.getId()))
            .sorted(Comparator.comparing(PostalOffice::getId))
            .toList();
        for (PostalOffice po : archivedArea1) {
            System.out.println(String.format("ID: %d | Name: %s", po.getId(), po.getName()));
        }

        System.out.println("\n=== DEBUG: ALL CONNECTIVITY RECORDS FOR ACTIVE OFFICES AT Q4 END ===");
        int count = 0;
        for (Integer officeId : activeAtQEndIds) {
            PostalOffice po = allOffices.stream().filter(p -> p.getId().equals(officeId)).findFirst().orElse(null);
            if (po != null) {
                System.out.println(String.format("ID: %d | Name: %s | Area: %d", officeId, po.getName(), po.getArea() != null ? po.getArea().getId() : -1));
                count++;
            }
        }
        System.out.println("Total counted: " + count);

        System.out.println("--- LIST OF ALL NON-ARCHIVED OFFICES IN AREA 1 ---");
        for (PostalOffice po : nonArchivedArea1) {
            boolean isConnectedAtStart = activeAtQStartIds.contains(po.getId());
            boolean isConnectedAtEnd = activeAtQEndIds.contains(po.getId());
            boolean isNewlyConnected = newlyConnectedIds.contains(po.getId());
            System.out.println(String.format("ID: %d | Name: %s | ConnAtStart: %b | ConnAtEnd: %b | NewlyConn: %b | ConnStatusCol: %b",
                po.getId(), po.getName(), isConnectedAtStart, isConnectedAtEnd, isNewlyConnected, po.getConnectionStatus()));
        }
    }

}
