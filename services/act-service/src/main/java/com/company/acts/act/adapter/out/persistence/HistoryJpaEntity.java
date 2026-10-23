package com.company.acts.act.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "act_history")
public class HistoryJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "act_id")
    private UUID actId;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "action")
    private String action;

    @Column(name = "details")
    private String details;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getActId() {
        return actId;
    }

    public void setActId(UUID actId) {
        this.actId = actId;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
