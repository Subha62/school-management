package com.schoolmanagement.announcement.entity;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A notice for an audience. It is visible once it is published, from {@code publishAt} (when set) until
 * {@code expiresAt} (when set), and only to people who belong to the audience.
 */
@Entity
@Table(name = "announcements")
public class Announcement extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnouncementAudience audience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnnouncementStatus status = AnnouncementStatus.DRAFT;

    private Instant publishAt;
    private Instant expiresAt;
    private Instant publishedAt;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    protected Announcement() {
    }

    public Announcement(UUID schoolId, UUID createdBy, String title, String body, AnnouncementAudience audience,
            Section section, Instant publishAt, Instant expiresAt) {
        this.schoolId = schoolId;
        this.createdBy = createdBy;
        this.audience = audience;
        this.section = section;
        edit(title, body, publishAt, expiresAt);
    }

    public void edit(String title, String body, Instant publishAt, Instant expiresAt) {
        this.title = title;
        this.body = body;
        this.publishAt = publishAt;
        this.expiresAt = expiresAt;
    }

    /** Only a draft can change who it is for; afterwards people may already have read it. */
    public void retarget(AnnouncementAudience audience, Section section) {
        this.audience = audience;
        this.section = section;
    }

    public void publish(Instant now) {
        if (status != AnnouncementStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft can be published.");
        }
        status = AnnouncementStatus.PUBLISHED;
        publishedAt = now;
    }

    public void archive() {
        if (status != AnnouncementStatus.PUBLISHED) {
            throw new BusinessRuleException("Only a published announcement can be archived.");
        }
        status = AnnouncementStatus.ARCHIVED;
    }

    /** Published, already started and not yet expired. */
    public boolean isLive(Instant now) {
        return status == AnnouncementStatus.PUBLISHED && (publishAt == null || !publishAt.isAfter(now))
                && (expiresAt == null || expiresAt.isAfter(now));
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public AnnouncementAudience getAudience() {
        return audience;
    }

    public Section getSection() {
        return section;
    }

    public AnnouncementStatus getStatus() {
        return status;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
