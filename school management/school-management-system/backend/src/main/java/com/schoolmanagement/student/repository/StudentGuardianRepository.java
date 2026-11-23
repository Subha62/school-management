package com.schoolmanagement.student.repository;

import com.schoolmanagement.student.entity.StudentGuardian;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, UUID> {

    @EntityGraph(attributePaths = "guardian")
    List<StudentGuardian> findAllByStudentIdOrderByCreatedAtAsc(UUID studentId);

    @EntityGraph(attributePaths = {"guardian", "student"})
    Optional<StudentGuardian> findByIdAndStudentId(UUID id, UUID studentId);

    boolean existsByStudentIdAndGuardianId(UUID studentId, UUID guardianId);

    boolean existsByGuardianId(UUID guardianId);

    boolean existsByStudentId(UUID studentId);

    Optional<StudentGuardian> findByStudentIdAndPrimaryGuardianTrue(UUID studentId);

    @Query("select sg.guardian.id, count(sg) from StudentGuardian sg where sg.guardian.id in :ids "
            + "group by sg.guardian.id")
    List<Object[]> countStudentsByGuardian(@Param("ids") Collection<UUID> guardianIds);
}
