package com.schoolmanagement.student.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** School-specific data of a student. Name, email and phone live on the linked user account. */
@Entity
@Table(name = "student_profiles")
public class StudentProfile extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private String admissionNumber;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    @Column(nullable = false)
    private LocalDate admissionDate;

    private String bloodGroup;

    @Column(nullable = false)
    private String addressLine1;

    private String addressLine2;

    @Column(nullable = false)
    private String city;

    private String state;
    private String postalCode;

    @Column(nullable = false)
    private String emergencyContactName;

    @Column(nullable = false)
    private String emergencyContactPhone;

    private String profilePhotoKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudentStatus status = StudentStatus.ACTIVE;

    protected StudentProfile() {
    }

    public StudentProfile(UUID schoolId, User user, String admissionNumber) {
        this.schoolId = schoolId;
        this.user = user;
        this.admissionNumber = admissionNumber;
    }

    /** Everything an administrator may edit. The admission number and the account link never change. */
    public void update(LocalDate dateOfBirth, Gender gender, LocalDate admissionDate, String bloodGroup,
            String addressLine1, String addressLine2, String city, String state, String postalCode,
            String emergencyContactName, String emergencyContactPhone, StudentStatus status) {
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.admissionDate = admissionDate;
        this.bloodGroup = bloodGroup;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.emergencyContactName = emergencyContactName;
        this.emergencyContactPhone = emergencyContactPhone;
        this.status = status;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public User getUser() {
        return user;
    }

    public String getAdmissionNumber() {
        return admissionNumber;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public String getProfilePhotoKey() {
        return profilePhotoKey;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == StudentStatus.ACTIVE;
    }
}
