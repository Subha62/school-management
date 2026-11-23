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
import java.util.UUID;

/** The class teacher's overall comment on a student's exam. */
@Entity
@Table(name = "exam_comments")
public class ExamComment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false, updatable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private Section section;

    @Column(nullable = false)
    private String comment;

    @Column(nullable = false)
    private UUID writtenBy;

    protected ExamComment() {
    }

    public ExamComment(Exam exam, StudentProfile student, Section section, String comment, UUID writtenBy) {
        this.exam = exam;
        this.student = student;
        this.section = section;
        write(comment, writtenBy);
    }

    public void write(String comment, UUID writtenBy) {
        this.comment = comment;
        this.writtenBy = writtenBy;
    }

    public Exam getExam() {
        return exam;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Section getSection() {
        return section;
    }

    public String getComment() {
        return comment;
    }

    public UUID getWrittenBy() {
        return writtenBy;
    }
}
