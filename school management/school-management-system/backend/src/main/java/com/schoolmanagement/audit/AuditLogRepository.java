package com.schoolmanagement.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    java.util.Optional<AuditLog> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Query("select distinct a.action from AuditLog a where a.schoolId = :schoolId order by a.action")
    List<String> findActions(@Param("schoolId") UUID schoolId);

    @Query("select distinct a.entityType from AuditLog a where a.schoolId = :schoolId and a.entityType is not null "
            + "order by a.entityType")
    List<String> findEntityTypes(@Param("schoolId") UUID schoolId);
}
