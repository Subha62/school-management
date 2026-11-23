package com.schoolmanagement.academic.repository;

import com.schoolmanagement.academic.entity.AcademicYear;
import com.schoolmanagement.academic.entity.AcademicYearStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID>, JpaSpecificationExecutor<AcademicYear> {

    Optional<AcademicYear> findByIdAndSchoolId(UUID id, UUID schoolId);

    boolean existsBySchoolIdAndNameIgnoreCase(UUID schoolId, String name);

    boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(UUID schoolId, String name, UUID id);

    boolean existsBySchoolIdAndStatus(UUID schoolId, AcademicYearStatus status);

    java.util.Optional<AcademicYear> findFirstBySchoolIdAndStatus(UUID schoolId, AcademicYearStatus status);

    /** Date ranges overlap when each starts on or before the other ends. */
    boolean existsBySchoolIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(UUID schoolId, LocalDate end,
            LocalDate start);

    boolean existsBySchoolIdAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(UUID schoolId,
            LocalDate end, LocalDate start, UUID id);
}
