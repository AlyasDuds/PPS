package com.pps.profilesystem.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity to store frozen quarterly connectivity snapshots.
 * Once a quarter completes, its data is saved here and cannot be modified.
 * This ensures historical data integrity.
 */
@Entity
@Table(name = "quarterly_snapshot",
       indexes = {
           @Index(name = "idx_snapshot_year_quarter", columnList = "year, quarter"),
           @Index(name = "idx_snapshot_area", columnList = "area_id")
       })
public class QuarterlySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer snapshotId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "quarter", nullable = false, length = 2)
    private String quarter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    @Column(name = "connected")
    private Long connectedCount;

    @Column(name = "newly_connected")
    private Long newlyConnectedCount;

    @Column(name = "disconnected")
    private Long disconnectedCount;

    @Column(name = "newly_disconnected")
    private Long newlyDisconnectedCount;

    @Column(name = "total")
    private Long totalOffices;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Integer getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Integer snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getQuarter() {
        return quarter;
    }

    public void setQuarter(String quarter) {
        this.quarter = quarter;
    }

    public Area getArea() {
        return area;
    }

    public void setArea(Area area) {
        this.area = area;
    }

    public Long getConnectedCount() {
        return connectedCount;
    }

    public void setConnectedCount(Long connectedCount) {
        this.connectedCount = connectedCount;
    }

    public Long getNewlyConnectedCount() {
        return newlyConnectedCount;
    }

    public void setNewlyConnectedCount(Long newlyConnectedCount) {
        this.newlyConnectedCount = newlyConnectedCount;
    }

    public Long getDisconnectedCount() {
        return disconnectedCount;
    }

    public void setDisconnectedCount(Long disconnectedCount) {
        this.disconnectedCount = disconnectedCount;
    }

    public Long getNewlyDisconnectedCount() {
        return newlyDisconnectedCount;
    }

    public void setNewlyDisconnectedCount(Long newlyDisconnectedCount) {
        this.newlyDisconnectedCount = newlyDisconnectedCount;
    }

    public Long getTotalOffices() {
        return totalOffices;
    }

    public void setTotalOffices(Long totalOffices) {
        this.totalOffices = totalOffices;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
