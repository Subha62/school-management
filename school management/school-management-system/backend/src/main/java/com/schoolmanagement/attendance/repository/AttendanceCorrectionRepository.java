package com.schoolmanagement.attendance.repository;

import com.schoolmanagement.attendance.entity.AttendanceCorrection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, UUID> {

    @EntityGraph(attributePaths = {"student", "student.user"})
    List<AttendanceCorrection> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
