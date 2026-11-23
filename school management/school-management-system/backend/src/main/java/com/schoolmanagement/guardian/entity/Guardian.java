package com.schoolmanagement.guardian.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "guardians")
public class Guardian extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GuardianRelationship relationship;

    private String email;

    @Column(nullable = false)
    private String phoneNumber;

    private String occupation;
    private String address;

    @Column(nullable = false)
    private boolean emergencyContact;

    protected Guardian() {
    }

    public Guardian(UUID schoolId, String firstName, String lastName, GuardianRelationship relationship,
            String email, String phoneNumber, String occupation, String address, boolean emergencyContact) {
        this.schoolId = schoolId;
        update(firstName, lastName, relationship, email, phoneNumber, occupation, address, emergencyContact);
    }

    public void update(String firstName, String lastName, GuardianRelationship relationship, String email,
            String phoneNumber, String occupation, String address, boolean emergencyContact) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.relationship = relationship;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.occupation = occupation;
        this.address = address;
        this.emergencyContact = emergencyContact;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public GuardianRelationship getRelationship() {
        return relationship;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getAddress() {
        return address;
    }

    public boolean isEmergencyContact() {
        return emergencyContact;
    }
}
