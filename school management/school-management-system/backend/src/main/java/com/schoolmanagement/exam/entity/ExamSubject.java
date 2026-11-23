package com.schoolmanagement.exam.entity;

import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A subject examined in an exam, with its own maximum and pass mark. */
@Entity
@Table(name = "exam_subjects")
public class ExamSubject extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false, updatable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false, updatable = false)
    private Subject subject;

    @Column(nullable = false)
    private BigDecimal maxMarks;

    @Column(nullable = false)
    private BigDecimal passMarks;

    private LocalDate examDate;

    protected ExamSubject() {
    }

    public ExamSubject(Exam exam, Subject subject, BigDecimal maxMarks, BigDecimal passMarks, LocalDate examDate) {
        this.exam = exam;
        this.subject = subject;
        update(maxMarks, passMarks, examDate);
    }

    public void update(BigDecimal maxMarks, BigDecimal passMarks, LocalDate examDate) {
        this.maxMarks = maxMarks;
        this.passMarks = passMarks;
        this.examDate = examDate;
    }

    public Exam getExam() {
        return exam;
    }

    public Subject getSubject() {
        return subject;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public BigDecimal getPassMarks() {
        return passMarks;
    }

    public LocalDate getExamDate() {
        return examDate;
    }
}
