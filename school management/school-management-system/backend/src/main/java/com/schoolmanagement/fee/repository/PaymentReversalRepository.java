package com.schoolmanagement.fee.repository;

import com.schoolmanagement.fee.entity.PaymentReversal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentReversalRepository extends JpaRepository<PaymentReversal, UUID> {

    boolean existsByPaymentId(UUID paymentId);

    @Query("select r from PaymentReversal r where r.payment.id in :paymentIds")
    List<PaymentReversal> findAllByPaymentIds(@Param("paymentIds") Collection<UUID> paymentIds);

    @Query("select coalesce(sum(p.amount), 0), count(p) from PaymentReversal r join r.payment p "
            + "where p.schoolId = :schoolId and p.paidOn between :from and :to")
    List<Object[]> totalReversed(@Param("schoolId") UUID schoolId, @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);
}
