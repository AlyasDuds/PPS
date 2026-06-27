package com.pps.profilesystem.Controller;

import com.pps.profilesystem.Entity.Connectivity;
import com.pps.profilesystem.Entity.PostalOffice;
import com.pps.profilesystem.Repository.ConnectivityRepository;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
public class CleanupController {

    @Autowired
    private ConnectivityRepository connectivityRepository;

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    @GetMapping("/api/cleanup-duplicates")
    @Transactional
    public String cleanup() {
        int deleted = 0;
        List<PostalOffice> allOffices = postalOfficeRepository.findAll();
        for (PostalOffice office : allOffices) {
            if (Boolean.TRUE.equals(office.getIsConnected())) {
                List<Connectivity> conns = connectivityRepository.findByPostalOfficeId(office.getId());
                // Sort by date connected descending so the latest is first
                conns.sort((a, b) -> {
                    if (a.getDateConnected() == null && b.getDateConnected() == null) return 0;
                    if (a.getDateConnected() == null) return 1;
                    if (b.getDateConnected() == null) return -1;
                    return b.getDateConnected().compareTo(a.getDateConnected());
                });

                // If an active office has more than 1 connectivity record, and the latest one has no disconnected date,
                // and the previous one was closed very recently (e.g. duplicate created by the bug)
                if (conns.size() > 1) {
                    Connectivity latest = conns.get(0);
                    if (latest.getDateDisconnected() == null) {
                        for (int i = 1; i < conns.size(); i++) {
                            Connectivity old = conns.get(i);
                            // Only delete the old one if it has a disconnected date and is from 2024 or 2025
                            if (old.getDateDisconnected() != null && old.getDateDisconnected().getYear() >= 2024) {
                                connectivityRepository.delete(old);
                                deleted++;
                            }
                        }
                    }
                }
            }
        }
        return "Deleted " + deleted + " duplicate connectivity records.";
    }
}
