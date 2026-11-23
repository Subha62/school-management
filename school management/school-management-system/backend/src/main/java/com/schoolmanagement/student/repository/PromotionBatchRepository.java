package com.schoolmanagement.student.repository;

import com.schoolmanagement.student.entity.PromotionBatch;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionBatchRepository extends JpaRepository<PromotionBatch, UUID> {

    Page<PromotionBatch> findAllBySchoolId(UUID schoolId, Pageable pageable);
}
