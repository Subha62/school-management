package com.schoolmanagement.student.dto;

import com.schoolmanagement.guardian.entity.Guardian;
import com.schoolmanagement.guardian.entity.GuardianRelationship;
import com.schoolmanagement.student.entity.StudentGuardian;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public final class StudentGuardianDtos {

    private StudentGuardianDtos() {
    }

    /** {@code guardianId} is used when linking; {@code version} when updating the flags. */
    public record Request(
            UUID guardianId,
            @NotNull(message = "Primary flag is required") Boolean primaryGuardian,
            @NotNull(message = "Notifications flag is required") Boolean receivesNotifications,
            @NotNull(message = "Pickup flag is required") Boolean authorizedPickup,
            Long version) {
    }

    public record Response(UUID id, UUID guardianId, String firstName, String lastName,
            GuardianRelationship relationship, String phoneNumber, String email, boolean primaryGuardian,
            boolean receivesNotifications, boolean authorizedPickup, long version) {

        public static Response from(StudentGuardian link) {
            Guardian guardian = link.getGuardian();
            return new Response(link.getId(), guardian.getId(), guardian.getFirstName(), guardian.getLastName(),
                    guardian.getRelationship(), guardian.getPhoneNumber(), guardian.getEmail(),
                    link.isPrimaryGuardian(), link.isReceivesNotifications(), link.isAuthorizedPickup(),
                    link.getVersion());
        }
    }
}
