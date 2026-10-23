package com.company.acts.act.adapter.out.persistence;

import com.company.acts.common.persistence.AuditedJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "act_photo")
public class PhotoJpaEntity extends AuditedJpaEntity {

    @Column(name = "act_id")
    private UUID actId;

    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "caption")
    private String caption;

    @Column(name = "position_no")
    private int position;

    public UUID getActId() {
        return actId;
    }

    public void setActId(UUID actId) {
        this.actId = actId;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public void setMediaId(UUID mediaId) {
        this.mediaId = mediaId;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
