package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.domain.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "act_participant")
public class ParticipantJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "act_id")
    private UUID actId;

    @Column(name = "engineer_id")
    private UUID engineerId;

    @Column(name = "keycloak_user_id")
    private String keycloakUserId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "position")
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private ParticipantRole role;

    @Column(name = "created_at")
    private Instant createdAt;

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

    public UUID getEngineerId() {
        return engineerId;
    }

    public void setEngineerId(UUID engineerId) {
        this.engineerId = engineerId;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public ParticipantRole getRole() {
        return role;
    }

    public void setRole(ParticipantRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
