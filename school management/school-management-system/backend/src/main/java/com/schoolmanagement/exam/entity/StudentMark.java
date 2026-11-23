package com.schoolmanagement.exam.entity;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** What one student scored in one exam subject, or that they were absent. A row exists once something is entered. */
@Entity
@Table(name = "student_marks")
public class StudentMark extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_subject_id", nullable = false, updatable = false)
    private ExamSubject examSubject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    private BigDecimal marks;

    @Column(nullable = false)
    private boolean absent;

    private String remarks;

    @Column(nullable = false)
    private UUID enteredBy;

    protected StudentMark() {
    }

    public StudentMark(ExamSubject examSubject, StudentProfile student, Section section) {
        this.examSubject = examSubject;
        this.student = student;
        this.section = section;
    }

    /** Absent students have no marks; present students must have them. */
    public void record(BigDecimal marks, boolean absent, String remarks, UUID enteredBy) {
        this.marks = absent ? null : marks;
        this.absent = absent;
        this.remarks = remarks;
        this.enteredBy = enteredBy;
    }

    public ExamSubject getExamSubject() {
        return examSubject;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Section getSection() {
        return section;
    }

    public BigDecimal getMarks() {
        return marks;
    }

    public boolean isAbsent() {
        return absent;
    }

    public String getRemarks() {
        return remarks;
    }

    public UUID getEnteredBy() {
        return enteredBy;
    }
}
