package com.pps.profilesystem.Service;

import com.pps.profilesystem.Entity.QuarterlySnapshot;
import com.pps.profilesystem.Repository.AreaRepository;
import com.pps.profilesystem.Repository.QuarterlySnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service to manage quarterly connectivity snapshots.
 * Snapshots are created when a quarter completes to freeze historical data.
 */
@Service
@Transactional
public class QuarterlySnapshotService {

    @Autowired
    private QuarterlySnapshotRepository snapshotRepository;

    @Autowired
    private AreaRepository areaRepository;

    /**
     * Create a snapshot for a specific year, quarter, and area with given stats
     */
    public QuarterlySnapshot createSnapshot(Integer year, String quarter, Integer areaId, 
                                           long connectedCount, long newlyConnectedCount,
                                           long disconnectedCount, long newlyDisconnectedCount,
                                           long totalOffices) {
        // Check if snapshot already exists
        if (snapshotRepository.existsByYearAndQuarterAndAreaId(year, quarter, areaId)) {
            // Update existing snapshot
            QuarterlySnapshot existing = snapshotRepository
                    .findByYearAndQuarterAndAreaId(year, quarter, areaId)
                    .orElse(null);
            if (existing != null) {
                existing.setConnectedCount(connectedCount);
                existing.setNewlyConnectedCount(newlyConnectedCount);
                existing.setDisconnectedCount(disconnectedCount);
                existing.setNewlyDisconnectedCount(newlyDisconnectedCount);
                existing.setTotalOffices(totalOffices);
                return snapshotRepository.save(existing);
            }
        }

        // Create new snapshot
        QuarterlySnapshot snapshot = new QuarterlySnapshot();
        snapshot.setYear(year);
        snapshot.setQuarter(quarter);
        
        if (areaId != null) {
            areaRepository.findById(areaId).ifPresent(snapshot::setArea);
        }

        snapshot.setConnectedCount(connectedCount);
        snapshot.setNewlyConnectedCount(newlyConnectedCount);
        snapshot.setDisconnectedCount(disconnectedCount);
        snapshot.setNewlyDisconnectedCount(newlyDisconnectedCount);
        snapshot.setTotalOffices(totalOffices);

        return snapshotRepository.save(snapshot);
    }

    /**
     * Create snapshots for all areas for a specific year and quarter
     */
    public void createSnapshotsForAllAreas(Integer year, String quarter) {
        // This will be called by ReportController with the computed stats
        // The actual creation will be done per-area with the stats
    }

    /**
     * Get snapshot data for a specific year, quarter, and area
     */
    public QuarterlySnapshot getSnapshot(Integer year, String quarter, Integer areaId) {
        return snapshotRepository.findByYearAndQuarterAndAreaId(year, quarter, areaId).orElse(null);
    }

    /**
     * Check if a snapshot exists for the given parameters
     */
    public boolean hasSnapshot(Integer year, String quarter, Integer areaId) {
        return snapshotRepository.existsByYearAndQuarterAndAreaId(year, quarter, areaId);
    }

    /**
     * Check if a quarter is completed (not the current quarter)
     */
    public boolean isQuarterCompleted(Integer year, String quarter) {
        LocalDateTime now = LocalDateTime.now();
        int currentYear = now.getYear();
        int currentQuarter = (now.getMonthValue() - 1) / 3 + 1;

        if (year > currentYear) {
            return false; // Future year
        }
        if (year < currentYear) {
            return true; // Past year
        }
        
        // Same year - check if quarter is before current quarter
        int quarterNum = Integer.parseInt(quarter.replace("Q", ""));
        return quarterNum < currentQuarter;
    }

    /**
     * Get the current year and quarter
     */
    public int[] getCurrentYearAndQuarter() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        return new int[]{year, quarter};
    }
}
