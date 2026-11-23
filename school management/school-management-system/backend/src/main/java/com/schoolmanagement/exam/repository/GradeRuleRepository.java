package com.schoolmanagement.exam.repository;

import com.schoolmanagement.exam.entity.GradeRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeRuleRepository extends JpaRepository<GradeRule, UUID>, JpaSpecificationExecutor<GradeRule> {

    Optional<GradeRule> findByIdAndSchoolId(UUID id, UUID schoolId);

    List<GradeRule> findAllBySchoolIdOrderByMinPercentAsc(UUID schoolId);

    boolean existsBySchoolIdAndGradeIgnoreCase(UUID schoolId, String grade);

    boolean existsBySchoolIdAndGradeIgnoreCaseAndIdNot(UUID schoolId, String grade, UUID id);

    /** Another band of the school that shares any percentage with the given range. */
    @Query("select count(r) > 0 from GradeRule r where r.schoolId = :schoolId and r.id <> :excludeId "
            + "and r.minPercent <= :max and r.maxPercent >= :min")
    boolean overlaps(@Param("schoolId") UUID schoolId, @Param("min") java.math.BigDecimal min,
            @Param("max") java.math.BigDecimal max, @Param("excludeId") UUID excludeId);

    /** Serialises grade-scale writes of one school, so two requests cannot both pass the overlap check. */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:key))) as locked",
            nativeQuery = true)
    long lockWrites(@Param("key") String key);
}
