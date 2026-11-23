package com.schoolmanagement.announcement.dto;

import com.schoolmanagement.announcement.entity.AnnouncementAudience;
import com.schoolmanagement.announcement.entity.AnnouncementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AnnouncementDtos {

    private AnnouncementDtos() {
    }

    /** {@code sectionId} is required for the SECTION audience and must be absent for the others. */
    public record Request(
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be at most 200 characters") String title,
            @NotBlank(message = "Message is required") @Size(max = 5000, message = "Message must be at most 5000 characters") String body,
            @NotNull(message = "Audience is required") AnnouncementAudience audience,
            UUID sectionId,
            Instant publishAt,
            Instant expiresAt,
            Long version) {
    }

    /**
     * One announcement. {@code readCount} is filled for people who manage announcements and left null in a reader's
     * feed; {@code read} is about the person asking. {@code live} says whether it is visible to its audience now.
     */
    public record Response(UUID id, String title, String body, AnnouncementAudience audience, UUID sectionId,
            String sectionName, AnnouncementStatus status, Instant publishAt, Instant expiresAt, Instant publishedAt,
            String authorName, Instant createdAt, Long readCount, boolean read, boolean live, long version) {
    }

    public record UnreadCount(long count) {
    }
}
