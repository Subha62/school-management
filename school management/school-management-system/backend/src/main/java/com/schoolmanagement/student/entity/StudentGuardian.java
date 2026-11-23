package com.schoolmanagement.student.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.guardian.entity.Guardian;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_guardians")
public class StudentGuardian extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_id", nullable = false, updatable = false)
    private Guardian guardian;

    @Column(nullable = false)
    private boolean primaryGuardian;

    @Column(nullable = false)
    private boolean receivesNotifications;

    @Column(nullable = false)
    private boolean authorizedPickup;

    protected StudentGuardian() {
    }

    public StudentGuardian(StudentProfile student, Guardian guardian, boolean primaryGuardian,
            boolean receivesNotifications, boolean authorizedPickup) {
        this.student = student;
        this.guardian = guardian;
        update(primaryGuardian, receivesNotifications, authorizedPickup);
    }

    public void update(boolean primaryGuardian, boolean receivesNotifications, boolean authorizedPickup) {
        this.primaryGuardian = primaryGuardian;
        this.receivesNotifications = receivesNotifications;
        this.authorizedPickup = authorizedPickup;
    }

    public void demoteFromPrimary() {
        this.primaryGuardian = false;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Guardian getGuardian() {
        return guardian;
    }

    public boolean isPrimaryGuardian() {
        return primaryGuardian;
    }

    public boolean isReceivesNotifications() {
        return receivesNotifications;
    }

    public boolean isAuthorizedPickup() {
        return authorizedPickup;
    }
}
