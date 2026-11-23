package com.schoolmanagement.academic.service;

import com.schoolmanagement.academic.dto.AcademicYearDtos;
import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicYearService {

    static final String ENTITY = "AcademicYear";

    private final AcademicYearRepository years;
    private final SectionRepository sections;
    private final AuditEventPublisher audit;

    public AcademicYearService(AcademicYearRepository years, SectionRepository sections, AuditEventPublisher audit) {
        this.years = years;
        this.sections = sections;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<AcademicYear> search(UUID schoolId, AcademicYearStatus status, String search, Pageable pageable) {
        Specification<AcademicYear> spec = Specification.<AcademicYear>where(SearchSpecs.equalTo("schoolId", schoolId))
                .and(SearchSpecs.equalTo("status", status))
                .and(SearchSpecs.containsIgnoreCase("name", search));
        return years.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public AcademicYear get(UUID schoolId, UUID id) {
        return years.findByIdAndSchoolId(id, schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("Academic year"));
    }

    @Transactional
    public AcademicYear create(AuthenticatedUser actor, AcademicYearDtos.Request request) {
        UUID schoolId = actor.schoolId();
        String name = request.name().trim();
        validateDates(request.startDate(), request.endDate());
        if (years.existsBySchoolIdAndNameIgnoreCase(schoolId, name)) {
            throw new ConflictException("An academic year named \"" + name + "\" already exists.");
        }
        if (years.existsBySchoolIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(schoolId,
                request.endDate(), request.startDate())) {
            throw new ConflictException("The dates overlap another academic year.");
        }
        AcademicYear year = years.saveAndFlush(new AcademicYear(schoolId, name, request.startDate(), request.endDate()));
        audit.record(actor, "ACADEMIC_YEAR_CREATED", ENTITY, year.getId(), "Created academic year " + name);
        return year;
    }

    @Transactional
    public AcademicYear update(AuthenticatedUser actor, UUID id, AcademicYearDtos.Request request) {
        AcademicYear year = get(actor.schoolId(), id);
        OptimisticVersion.check(request.version(), year.getVersion());
        if (year.isClosed()) {
            throw new BusinessRuleException("A closed academic year cannot be changed.");
        }
        String name = request.name().trim();
        validateDates(request.startDate(), request.endDate());
        if (years.existsBySchoolIdAndNameIgnoreCaseAndIdNot(actor.schoolId(), name, id)) {
            throw new ConflictException("An academic year named \"" + name + "\" already exists.");
        }
        if (years.existsBySchoolIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(actor.schoolId(),
                request.endDate(), request.startDate(), id)) {
            throw new ConflictException("The dates overlap another academic year.");
        }
        year.update(name, request.startDate(), request.endDate());
        years.saveAndFlush(year);
        audit.record(actor, "ACADEMIC_YEAR_UPDATED", ENTITY, id, "Updated academic year " + name);
        return year;
    }

    /** Only one year may be active. Activation never closes another year implicitly. */
    @Transactional
    public AcademicYear activate(AuthenticatedUser actor, UUID id) {
        AcademicYear year = get(actor.schoolId(), id);
        if (years.existsBySchoolIdAndStatus(actor.schoolId(), AcademicYearStatus.ACTIVE)) {
            throw new ConflictException("Another academic year is already active. Close it before activating this one.");
        }
        year.activate();
        years.saveAndFlush(year);
        audit.record(actor, "ACADEMIC_YEAR_ACTIVATED", ENTITY, id, "Activated academic year " + year.getName());
        return year;
    }

    @Transactional
    public AcademicYear close(AuthenticatedUser actor, UUID id) {
        AcademicYear year = get(actor.schoolId(), id);
        year.close();
        years.saveAndFlush(year);
        audit.record(actor, "ACADEMIC_YEAR_CLOSED", ENTITY, id, "Closed academic year " + year.getName());
        return year;
    }

    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        AcademicYear year = get(actor.schoolId(), id);
        if (year.getStatus() != AcademicYearStatus.PLANNED) {
            throw new BusinessRuleException("Only a planned academic year can be deleted.");
        }
        if (sections.existsByAcademicYearId(id)) {
            throw new ConflictException("This academic year has sections. Delete them first.");
        }
        years.delete(year);
        audit.record(actor, "ACADEMIC_YEAR_DELETED", ENTITY, id, "Deleted academic year " + year.getName());
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("The end date must be after the start date.");
        }
    }
}
