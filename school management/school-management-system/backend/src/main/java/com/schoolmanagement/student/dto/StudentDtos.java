package com.schoolmanagement.student.dto;

import com.schoolmanagement.student.entity.Gender;
import com.schoolmanagement.student.entity.StudentProfile;
import com.schoolmanagement.student.entity.StudentStatus;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public final class StudentDtos {

    public static final String PHONE = "^[0-9+()\\-\\s]{6,30}$";
    public static final String BLOOD_GROUP = "^(A|B|AB|O)[+-]$";

    private StudentDtos() {
    }

    /**
     * {@code admissionNumber} and {@code password} are used on create only (the sign-in ID and initial password);
     * {@code version} and {@code status} are used on update. The server rejects missing create-only fields.
     */
    public record Request(
            @Size(max = 30, message = "Admission number must be at most 30 characters") String admissionNumber,
            @Size(max = 128, message = "Password is too long") String password,
            @NotBlank(message = "First name is required") @Size(max = 100, message = "First name must be at most 100 characters") String firstName,
            @NotBlank(message = "Last name is required") @Size(max = 100, message = "Last name must be at most 100 characters") String lastName,
            @NotBlank(message = "Email is required") @Email(message = "Email must be valid") @Size(max = 254, message = "Email is too long") String email,
            @Pattern(regexp = PHONE, message = "Phone number is not valid") String phoneNumber,
            @NotNull(message = "Date of birth is required") @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
            @NotNull(message = "Gender is required") Gender gender,
            @NotNull(message = "Admission date is required") LocalDate admissionDate,
            @Pattern(regexp = BLOOD_GROUP, message = "Blood group must be like A+, O-, AB+") String bloodGroup,
            @NotBlank(message = "Address is required") @Size(max = 200, message = "Address must be at most 200 characters") String addressLine1,
            @Size(max = 200, message = "Address must be at most 200 characters") String addressLine2,
            @NotBlank(message = "City is required") @Size(max = 100, message = "City must be at most 100 characters") String city,
            @Size(max = 100, message = "State must be at most 100 characters") String state,
            @Size(max = 20, message = "Postal code must be at most 20 characters") String postalCode,
            @NotBlank(message = "Emergency contact name is required") @Size(max = 100, message = "Name must be at most 100 characters") String emergencyContactName,
            @NotBlank(message = "Emergency contact phone is required") @Pattern(regexp = PHONE, message = "Phone number is not valid") String emergencyContactPhone,
            StudentStatus status,
            Long version) {
    }

    /** The student's current (active) placement, shown next to the profile. */
    public record EnrollmentRef(UUID enrollmentId, String academicYear, String grade, String section,
            Integer rollNumber) {
    }

    public record Response(
            UUID id,
            UUID userId,
            String admissionNumber,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            LocalDate dateOfBirth,
            Gender gender,
            LocalDate admissionDate,
            String bloodGroup,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String emergencyContactName,
            String emergencyContactPhone,
            StudentStatus status,
            AccountStatus accountStatus,
            EnrollmentRef currentEnrollment,
            long version) {

        public static Response from(StudentProfile profile, EnrollmentRef currentEnrollment) {
            User user = profile.getUser();
            return new Response(profile.getId(), user.getId(), profile.getAdmissionNumber(), user.getFirstName(),
                    user.getLastName(), user.getEmail(), user.getPhoneNumber(), profile.getDateOfBirth(),
                    profile.getGender(), profile.getAdmissionDate(), profile.getBloodGroup(),
                    profile.getAddressLine1(), profile.getAddressLine2(), profile.getCity(), profile.getState(),
                    profile.getPostalCode(), profile.getEmergencyContactName(), profile.getEmergencyContactPhone(),
                    profile.getStatus(), user.getAccountStatus(), currentEnrollment, profile.getVersion());
        }
    }
}
