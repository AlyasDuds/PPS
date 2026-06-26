package com.pps.profilesystem.Service;

import com.pps.profilesystem.Entity.Connectivity;
import com.pps.profilesystem.Entity.PostalOffice;
import com.pps.profilesystem.Repository.ConnectivityRepository;
import com.pps.profilesystem.Repository.PostalOfficeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Year-End Rollover Service
 * 
 * Automatically performs year-end connectivity record rollover on January 1st.
 * This ensures that each year has its own separate connectivity records,
 * preventing previous year data from being modified and enabling accurate
 * quarterly reporting per year.
 * 
 * Scheduled to run at 00:05 on January 1st every year.
 */
@Service
public class YearEndRolloverService {

    private static final Logger logger = LoggerFactory.getLogger(YearEndRolloverService.class);

    @Autowired
    private ConnectivityRepository connectivityRepository;

    @Autowired
    private PostalOfficeRepository postalOfficeRepository;

    /**
     * Scheduled task to perform year-end rollover.
     * Runs at 00:05 on January 1st every year.
     * 
     * Process:
     * 1. Find all open connectivity records (dateDisconnected is null)
     * 2. Close previous year records at Dec 31, 23:59:59
     * 3. Create new records for current year starting Jan 1, 00:00:00
     */
    @Scheduled(cron = "0 5 0 1 1 ?")
    @Transactional
    public void performYearEndRollover() {
        int currentYear = LocalDateTime.now().getYear();
        int previousYear = currentYear - 1;

        logger.info("Starting year-end rollover from {} to {}", previousYear, currentYear);

        // Find all open connectivity records (no dateDisconnected)
        List<Connectivity> openRecords = connectivityRepository.findByDateDisconnectedIsNull();

        int closedCount = 0;
        int createdCount = 0;

        for (Connectivity conn : openRecords) {
            // Skip if this is already a current year record (shouldn't happen on Jan 1)
            if (conn.getDateConnected() != null && conn.getDateConnected().getYear() >= currentYear) {
                logger.warn("Skipping record {} - already belongs to current year {}",
                    conn.getConnectivityId(), currentYear);
                continue;
            }

            // Close the previous year record at end of that year
            conn.setDateDisconnected(LocalDateTime.of(previousYear, 12, 31, 23, 59, 59));
            connectivityRepository.save(conn);
            closedCount++;

            // Create new record for current year
            Connectivity newConn = cloneConnectivityRecord(conn, currentYear);
            connectivityRepository.save(newConn);
            createdCount++;

            // Update the postal office's activeConnectivity pointer
            PostalOffice office = conn.getPostalOffice();
            if (office != null) {
                office.setActiveConnectivity(newConn);
                postalOfficeRepository.save(office);
            }

            logger.info("Rollover completed for office ID {}: closed {} record, created {} record",
                office != null ? office.getId() : "unknown", previousYear, currentYear);
        }

        logger.info("Year-end rollover completed: {} records closed, {} records created",
            closedCount, createdCount);
    }

    /**
     * Clones a connectivity record for the new year.
     * Preserves all fields except dates and ID.
     */
    private Connectivity cloneConnectivityRecord(Connectivity original, int newYear) {
        Connectivity clone = new Connectivity();
        
        clone.setPostalOffice(original.getPostalOffice());
        clone.setProvider(original.getProvider());
        clone.setDateConnected(LocalDateTime.of(newYear, 1, 1, 0, 0, 0));
        clone.setDateDisconnected(null); // Open for the new year
        
        // Copy plan & billing fields
        clone.setPlanName(original.getPlanName());
        clone.setPlanPrice(original.getPlanPrice());
        clone.setAccountNumber(original.getAccountNumber());
        clone.setPlanContract(original.getPlanContract());
        clone.setIsWired(original.getIsWired());
        clone.setIsWireless(original.getIsWireless());
        clone.setIsShared(original.getIsShared());
        clone.setIsFree(original.getIsFree());
        
        return clone;
    }

    /**
     * Manual trigger method for testing or ad-hoc rollover.
     * Can be called via an admin endpoint if needed.
     */
    @Transactional
    public void performRolloverManually() {
        logger.info("Manual year-end rollover triggered");
        performYearEndRollover();
    }
}
