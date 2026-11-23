package com.schoolmanagement.school.entity;

import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "schools")
public class School extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String schoolCode;

    private String address;
    private String phoneNumber;
    private String email;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean active = true;

    protected School() {
    }

    public School(String name, String schoolCode, String timezone) {
        this.name = name;
        this.schoolCode = schoolCode;
        this.timezone = timezone;
    }

    public String getName() {
        return name;
    }

    public String getSchoolCode() {
        return schoolCode;
    }

    public String getAddress() {
        return address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }
}
