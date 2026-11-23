package com.schoolmanagement.student.dto;

import com.schoolmanagement.academic.dto.SectionDtos;
import com.schoolmanagement.student.entity.EnrollmentStatus;
import com.schoolmanagement.student.entity.StudentEnrollment;
import com.schoolmanagement.student.entity.StudentProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EnrollmentDtos {

    public static final int MAX_PROMOTION_ITEMS = 500;

    private EnrollmentDtos() {
    }

    public record EnrollRequest(
            @NotNull(message = "Student is required") UUID studentId,
            @NotNull(message = "Section is required") UUID sectionId,
            @Min(value = 1, message = "Roll number must be at least 1") @Max(value = 9999, message = "Roll number must be at most 9999") Integer rollNumber,
            LocalDate enrollmentDate) {
    }

    public record TransferRequest(
            @NotNull(message = "Target section is required") UUID targetSectionId,
            @Min(value = 1, message = "Roll number must be at least 1") @Max(value = 9999, message = "Roll number must be at most 9999") Integer rollNumber) {
    }

    public record StudentRef(UUID id, String admissionNumber, String name) {

        public static StudentRef from(StudentProfile student) {
            return new StudentRef(student.getId(), student.getAdmissionNumber(),
                    (student.getUser().getFirstName() + " " + student.getUser().getLastName()).trim());
        }
    }

    public record Response(UUID id, StudentRef student, SectionDtos.NamedRef academicYear,
            SectionDtos.NamedRef gradeLevel, SectionDtos.NamedRef section, Integer rollNumber,
            LocalDate enrollmentDate, EnrollmentStatus status, UUID sourceEnrollmentId, UUID promotionBatchId,
            long version) {

        public static Response from(StudentEnrollment e) {
            return new Response(e.getId(), StudentRef.from(e.getStudent()),
                    new SectionDtos.NamedRef(e.getAcademicYear().getId(), e.getAcademicYear().getName()),
                    new SectionDtos.NamedRef(e.getGradeLevel().getId(), e.getGradeLevel().getName()),
                    new SectionDtos.NamedRef(e.getSection().getId(), e.getSection().getName()), e.getRollNumber(),
                    e.getEnrollmentDate(), e.getStatus(), e.getSourceEnrollmentId(), e.getPromotionBatchId(),
                    e.getVersion());
        }
    }

    public record PromotionItem(
            @NotNull(message = "Student is required") UUID studentId,
            @NotNull(message = "Target section is required") UUID targetSectionId,
            @Min(value = 1, message = "Roll number must be at least 1") @Max(value = 9999, message = "Roll number must be at most 9999") Integer rollNumber) {
    }

    /** One student per item, so the same request serves individual and bulk promotion. */
    public record PromotionRequest(
            @NotNull(message = "Source academic year is required") UUID sourceAcademicYearId,
            @NotNull(message = "Target academic year is required") UUID targetAcademicYearId,
            @NotEmpty(message = "Select at least one student") @Size(max = MAX_PROMOTION_ITEMS, message = "At most 500 students can be promoted at once") List<@Valid PromotionItem> items) {
    }

    public enum Outcome {
        PROMOTED,
        FAILED
    }

    public record PromotionResult(UUID studentId, String admissionNumber, String studentName, Outcome outcome,
            String reason, UUID newEnrollmentId, Integer rollNumber) {
    }

    public record PromotionSummary(UUID batchId, String sourceAcademicYear, String targetAcademicYear,
            String performedBy, Instant performedAt, int total, int promoted, int failed,
            List<PromotionResult> results) {
    }

    public record BatchResponse(UUID id, String sourceAcademicYear, String targetAcademicYear, String performedBy,
            Instant performedAt, int total, int promoted, int failed) {
    }
}
