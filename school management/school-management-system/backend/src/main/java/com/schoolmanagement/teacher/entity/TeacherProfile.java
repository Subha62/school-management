package com.schoolmanagement.teacher.entity;

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

/** School-specific data of a teacher. Name, email and phone live on the linked user account. */
@Entity
@Table(name = "teacher_profiles")
public class TeacherProfile extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private String employeeId;

    @Column(nullable = false)
    private LocalDate joiningDate;

    private String qualification;
    private String specialization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus employmentStatus = EmploymentStatus.ACTIVE;

    private String profilePhotoKey;

    protected TeacherProfile() {
    }

    public TeacherProfile(UUID schoolId, User user, String employeeId) {
        this.schoolId = schoolId;
        this.user = user;
        this.employeeId = employeeId;
    }

    public void update(LocalDate joiningDate, String qualification, String specialization,
            EmploymentStatus employmentStatus) {
        this.joiningDate = joiningDate;
        this.qualification = qualification;
        this.specialization = specialization;
        this.employmentStatus = employmentStatus;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public User getUser() {
        return user;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public LocalDate getJoiningDate() {
        return joiningDate;
    }

    public String getQualification() {
        return qualification;
    }

    public String getSpecialization() {
        return specialization;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public String getProfilePhotoKey() {
        return profilePhotoKey;
    }
}
