package com.schoolmanagement.timetable.entity;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** One weekly slot of a section: a subject taught by a teacher on a weekday, valid between two dates. */
@Entity
@Table(name = "timetable_entries")
public class TimetableEntry extends AuditableEntity {

    @Column(nullable = false, updatable = false)
    private UUID schoolId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false, updatable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    /** The teacher's user account. */
    @Column(nullable = false)
    private UUID teacherId;

    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int periodNumber;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false)
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimetableStatus status = TimetableStatus.ACTIVE;

    protected TimetableEntry() {
    }

    public TimetableEntry(Section section, Subject subject, UUID teacherId, String room, DayOfWeek dayOfWeek,
            int periodNumber, LocalTime startTime, LocalTime endTime, LocalDate effectiveFrom,
            LocalDate effectiveTo, TimetableStatus status) {
        this.schoolId = section.getSchoolId();
        this.academicYear = section.getAcademicYear();
        this.section = section;
        update(subject, teacherId, room, dayOfWeek, periodNumber, startTime, endTime, effectiveFrom, effectiveTo,
                status);
    }

    public void update(Subject subject, UUID teacherId, String room, DayOfWeek dayOfWeek, int periodNumber,
            LocalTime startTime, LocalTime endTime, LocalDate effectiveFrom, LocalDate effectiveTo,
            TimetableStatus status) {
        this.subject = subject;
        this.teacherId = teacherId;
        this.room = room;
        this.dayOfWeek = dayOfWeek;
        this.periodNumber = periodNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = status;
    }

    public boolean isActive() {
        return status == TimetableStatus.ACTIVE;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public Section getSection() {
        return section;
    }

    public Subject getSubject() {
        return subject;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public String getRoom() {
        return room;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public TimetableStatus getStatus() {
        return status;
    }
}
