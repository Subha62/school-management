package com.schoolmanagement.school.repository;

import com.schoolmanagement.school.entity.School;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    Optional<School> findBySchoolCode(String schoolCode);

    Optional<School> findFirstByActiveTrueOrderByCreatedAtAsc();
}
