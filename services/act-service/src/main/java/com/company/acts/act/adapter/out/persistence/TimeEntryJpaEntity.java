package com.company.acts.act.adapter.out.persistence;

import com.company.acts.common.persistence.AuditedJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "time_entry")
public class TimeEntryJpaEntity extends AuditedJpaEntity {

    @Column(name = "act_id")
    private UUID actId;

    @Column(name = "engineer_id")
    private UUID engineerId;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "hours")
    private BigDecimal hours;

    @Column(name = "travel_hours")
    private BigDecimal travelHours;

    @Column(name = "comment")
    private String comment;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "work_minutes")
    private int workMinutes;

    public UUID getActId() {
        return actId;
    }

    public void setActId(UUID actId) {
        this.actId = actId;
    }

    public UUID getEngineerId() {
        return engineerId;
    }

    public void setEngineerId(UUID engineerId) {
        this.engineerId = engineerId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public BigDecimal getTravelHours() {
        return travelHours;
    }

    public void setTravelHours(BigDecimal travelHours) {
        this.travelHours = travelHours;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public int getWorkMinutes() {
        return workMinutes;
    }

    public void setWorkMinutes(int workMinutes) {
        this.workMinutes = workMinutes;
    }
}
