package com.pps.profilesystem.Repository;

import com.pps.profilesystem.Entity.QuarterlySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuarterlySnapshotRepository extends JpaRepository<QuarterlySnapshot, Integer> {

    /**
     * Find snapshot by year, quarter, and area
     */
    Optional<QuarterlySnapshot> findByYearAndQuarterAndAreaId(Integer year, String quarter, Integer areaId);

    /**
     * Find snapshot by year and quarter (for all areas)
     */
    List<QuarterlySnapshot> findByYearAndQuarter(Integer year, String quarter);

    /**
     * Find all snapshots for a specific year
     */
    List<QuarterlySnapshot> findByYear(Integer year);

    /**
     * Find all snapshots for a specific area
     */
    List<QuarterlySnapshot> findByAreaId(Integer areaId);

    /**
     * Find all snapshots for a specific year and area
     */
    List<QuarterlySnapshot> findByYearAndAreaId(Integer year, Integer areaId);

    /**
     * Check if a snapshot exists for the given year, quarter, and area
     */
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM QuarterlySnapshot q WHERE q.year = :year AND q.quarter = :quarter AND (q.area.id = :areaId OR (q.area IS NULL AND :areaId IS NULL))")
    boolean existsByYearAndQuarterAndAreaId(@Param("year") Integer year, @Param("quarter") String quarter, @Param("areaId") Integer areaId);

    /**
     * Delete all snapshots for a specific year and quarter
     */
    void deleteByYearAndQuarter(Integer year, String quarter);
}
