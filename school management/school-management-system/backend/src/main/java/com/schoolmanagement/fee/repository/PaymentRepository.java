package com.schoolmanagement.fee.repository;

import com.schoolmanagement.fee.entity.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    @EntityGraph(attributePaths = {"student", "student.user", "studentFee", "studentFee.academicYear"})
    Optional<Payment> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"student", "student.user", "studentFee"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"student", "student.user", "studentFee"})
    List<Payment> findAllByStudentFeeIdOrderByCreatedAtDesc(UUID studentFeeId);
}
