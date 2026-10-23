package com.company.acts.act.adapter.out.persistence;

import com.company.acts.act.domain.PartSource;
import com.company.acts.common.persistence.AuditedJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "act_part_line")
public class PartLineJpaEntity extends AuditedJpaEntity {

    @Column(name = "act_id")
    private UUID actId;

    @Column(name = "position_no")
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private PartSource source;

    @Column(name = "catalog_item_id")
    private UUID catalogItemId;

    @Column(name = "name")
    private String name;

    @Column(name = "catalog_number")
    private String catalogNumber;

    @Column(name = "quantity")
    private BigDecimal quantity;

    @Column(name = "unit_code")
    private String unitCode;

    @Column(name = "notes")
    private String notes;

    public UUID getActId() {
        return actId;
    }

    public void setActId(UUID actId) {
        this.actId = actId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public PartSource getSource() {
        return source;
    }

    public void setSource(PartSource source) {
        this.source = source;
    }

    public UUID getCatalogItemId() {
        return catalogItemId;
    }

    public void setCatalogItemId(UUID catalogItemId) {
        this.catalogItemId = catalogItemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCatalogNumber() {
        return catalogNumber;
    }

    public void setCatalogNumber(String catalogNumber) {
        this.catalogNumber = catalogNumber;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public void setUnitCode(String unitCode) {
        this.unitCode = unitCode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
