package com.example.shortener.link;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

@Entity
@Table(name = "links")
public class Link implements Persistable<Long> {

    @Id
    private Long id;

    private String code;

    private String longUrl;

    private String ownerSub;

    private boolean active;

    private Instant createdAt;

    // The id is assigned by the app, so Spring Data cannot tell a new link from a stored one
    // by checking for a null id. This flag makes save() insert directly instead of merging.
    @Transient
    private boolean isNew = true;

    protected Link() {
        // for JPA
    }

    public Link(long id, String code, String longUrl, String ownerSub) {
        this.id = id;
        this.code = code;
        this.longUrl = longUrl;
        this.ownerSub = ownerSub;
        this.active = true;
        this.createdAt = Instant.now();
    }

    // Soft delete: the row stays, so the code is never reused and its statistics are kept.
    public void deactivate() {
        this.active = false;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getCode() {
        return code;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getOwnerSub() {
        return ownerSub;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
