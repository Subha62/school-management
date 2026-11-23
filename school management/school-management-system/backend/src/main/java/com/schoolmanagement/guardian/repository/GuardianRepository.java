package com.schoolmanagement.guardian.repository;

import com.schoolmanagement.guardian.entity.Guardian;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GuardianRepository extends JpaRepository<Guardian, UUID>, JpaSpecificationExecutor<Guardian> {

    Optional<Guardian> findByIdAndSchoolId(UUID id, UUID schoolId);
}
