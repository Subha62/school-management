package com.schoolmanagement.academic.service;

import com.schoolmanagement.academic.dto.SubjectDtos;
import com.schoolmanagement.academic.entity.Subject;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.academic.repository.SubjectRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.exam.repository.ExamSubjectRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

    private static final String ENTITY = "Subject";

    private final SubjectRepository subjects;
    private final SectionSubjectRepository sectionSubjects;
    private final ExamSubjectRepository examSubjects;
    private final AuditEventPublisher audit;

    public SubjectService(SubjectRepository subjects, SectionSubjectRepository sectionSubjects,
            ExamSubjectRepository examSubjects, AuditEventPublisher audit) {
        this.subjects = subjects;
        this.sectionSubjects = sectionSubjects;
        this.examSubjects = examSubjects;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Subject> search(UUID schoolId, Boolean active, String search, Pageable pageable) {
        Specification<Subject> spec = Specification.<Subject>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("active", active))
                .and(SearchSpecs.<Subject>containsIgnoreCase("name", search)
                        .or(SearchSpecs.containsIgnoreCase("code", search)));
        return subjects.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Subject get(UUID schoolId, UUID id) {
        return subjects.findByIdAndSchoolId(id, schoolId).orElseThrow(() -> new ResourceNotFoundException("Subject"));
    }

    @Transactional
    public Subject create(AuthenticatedUser actor, SubjectDtos.Request request) {
        String code = normalizeCode(request.code());
        if (subjects.existsBySchoolIdAndCodeIgnoreCase(actor.schoolId(), code)) {
            throw new ConflictException("A subject with code \"" + code + "\" already exists.");
        }
        Subject subject = subjects.saveAndFlush(new Subject(actor.schoolId(), code, request.name().trim(),
                blankToNull(request.description()), request.active()));
        audit.record(actor, "SUBJECT_CREATED", ENTITY, subject.getId(), "Created subject " + code);
        return subject;
    }

    @Transactional
    public Subject update(AuthenticatedUser actor, UUID id, SubjectDtos.Request request) {
        Subject subject = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), subject.getVersion());
        String code = normalizeCode(request.code());
        if (subjects.existsBySchoolIdAndCodeIgnoreCaseAndIdNot(actor.schoolId(), code, id)) {
            throw new ConflictException("A subject with code \"" + code + "\" already exists.");
        }
        subject.update(code, request.name().trim(), blankToNull(request.description()), request.active());
        subjects.saveAndFlush(subject);
        audit.record(actor, "SUBJECT_UPDATED", ENTITY, id, "Updated subject " + code);
        return subject;
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        Subject subject = get(actor.schoolId(), id);
        if (sectionSubjects.existsBySubjectId(id) || examSubjects.existsBySubjectId(id)) {
            throw new ConflictException("This subject is assigned to sections or exams. Mark it inactive instead of deleting it.");
        }
        subjects.delete(subject);
        audit.record(actor, "SUBJECT_DELETED", ENTITY, id, "Deleted subject " + subject.getCode());
    }

    /** Codes are stored upper case so "math" and "MATH" are the same subject. */
    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
