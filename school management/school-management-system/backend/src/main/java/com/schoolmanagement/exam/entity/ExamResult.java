package com.schoolmanagement.exam.entity;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.common.persistence.AuditableEntity;
import com.schoolmanagement.exam.dto.ExamDtos;
import com.schoolmanagement.student.entity.StudentProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A student's published result, frozen at publication together with the grade scale that produced it. */
@Entity
@Table(name = "exam_results")
public class ExamResult extends AuditableEntity {

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
    private String sectionName;

    private Integer rollNumber;

    @Column(nullable = false)
    private BigDecimal totalMarks;

    @Column(nullable = false)
    private BigDecimal totalMax;

    @Column(nullable = false)
    private BigDecimal percentage;

    @Column(nullable = false)
    private String grade;

    @Column(nullable = false)
    private String result;

    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<ExamDtos.SubjectResult> subjects;

    protected ExamResult() {
    }

    public ExamResult(Exam exam, StudentProfile student, Section section, String sectionName, Integer rollNumber,
            BigDecimal totalMarks, BigDecimal totalMax, BigDecimal percentage, String grade, String result,
            String comment, List<ExamDtos.SubjectResult> subjects) {
        this.exam = exam;
        this.student = student;
        this.section = section;
        this.sectionName = sectionName;
        this.rollNumber = rollNumber;
        this.totalMarks = totalMarks;
        this.totalMax = totalMax;
        this.percentage = percentage;
        this.grade = grade;
        this.result = result;
        this.comment = comment;
        this.subjects = subjects;
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

    public String getSectionName() {
        return sectionName;
    }

    public Integer getRollNumber() {
        return rollNumber;
    }

    public BigDecimal getTotalMarks() {
        return totalMarks;
    }

    public BigDecimal getTotalMax() {
        return totalMax;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public String getGrade() {
        return grade;
    }

    public String getResult() {
        return result;
    }

    public String getComment() {
        return comment;
    }

    public List<ExamDtos.SubjectResult> getSubjects() {
        return subjects;
    }
}
