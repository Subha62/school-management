package com.schoolmanagement.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolmanagement.academic.dto.AcademicYearDtos;
import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.service.AcademicYearService;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.security.AuthenticatedUser;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AcademicYearServiceTest {

    private final UUID schoolId = UUID.randomUUID();
    private final AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), schoolId, "admin",
            "admin@school.test", "Ada", "Admin", Set.of("ROLE_ADMIN"));
    private final AcademicYearRepository years = mock(AcademicYearRepository.class);
    private final SectionRepository sections = mock(SectionRepository.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private final AcademicYearService service = new AcademicYearService(years, sections, audit);
    private AcademicYear year;

    @BeforeEach
    void setUp() {
        year = new AcademicYear(schoolId, "2026-2027", LocalDate.of(2026, 6, 1), LocalDate.of(2027, 3, 31));
        ReflectionTestUtils.setField(year, "id", UUID.randomUUID());
        when(years.findByIdAndSchoolId(year.getId(), schoolId)).thenReturn(Optional.of(year));
    }

    @Test
    void statusMovesForwardOnlyPlannedToActiveToClosed() {
        assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.PLANNED);
        assertThatThrownBy(year::close).isInstanceOf(BusinessRuleException.class);

        year.activate();
        assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.ACTIVE);
        assertThatThrownBy(year::activate).isInstanceOf(BusinessRuleException.class);

        year.close();
        assertThat(year.isClosed()).isTrue();
        assertThatThrownBy(year::activate).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(year::close).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void activationIsAuditedWithTheActingAdministrator() {
        service.activate(admin, year.getId());

        verify(audit).record(eq(admin), eq("ACADEMIC_YEAR_ACTIVATED"), eq("AcademicYear"), eq(year.getId()), any());
    }

    @Test
    void activationIsRefusedAndNotAuditedWhileAnotherYearIsActive() {
        when(years.existsBySchoolIdAndStatus(schoolId, AcademicYearStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.activate(admin, year.getId())).isInstanceOf(ConflictException.class);

        assertThat(year.getStatus()).isEqualTo(AcademicYearStatus.PLANNED);
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void activeYearCannotBeDeleted() {
        year.activate();

        assertThatThrownBy(() -> service.delete(admin, year.getId())).isInstanceOf(BusinessRuleException.class);

        verify(years, never()).delete(any(AcademicYear.class));
    }

    @Test
    void updateWithoutTheCurrentVersionIsRejectedBeforeAnyChange() {
        var request = new AcademicYearDtos.Request("Renamed", year.getStartDate(), year.getEndDate(), null);

        assertThatThrownBy(() -> service.update(admin, year.getId(), request))
                .hasMessageContaining("version");

        assertThat(year.getName()).isEqualTo("2026-2027");
    }
}
