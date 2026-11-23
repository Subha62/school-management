package com.schoolmanagement.fee.service;

import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.config.FeeProperties;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.FeeStatus;
import com.schoolmanagement.fee.entity.PaymentMethod;
import com.schoolmanagement.school.service.SchoolTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.schoolmanagement.academic.repository.AcademicYearRepository;
import com.schoolmanagement.fee.repository.StudentFeeRepository;

/** Totals for administrators: what is billed, collected and still owed, and the money received in a period. */
@Service
public class FeeReportService {

    private static final long MAX_PERIOD_DAYS = 366;

    private final JdbcTemplate jdbc;
    private final StudentFeeRepository fees;
    private final AcademicYearRepository years;
    private final SchoolTime schoolTime;
    private final FeeProperties properties;

    public FeeReportService(JdbcTemplate jdbc, StudentFeeRepository fees, AcademicYearRepository years,
            SchoolTime schoolTime, FeeProperties properties) {
        this.jdbc = jdbc;
        this.fees = fees;
        this.years = years;
        this.schoolTime = schoolTime;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public FeeDtos.Summary summary(UUID schoolId, UUID yearId, UUID gradeId) {
        years.findByIdAndSchoolId(yearId, schoolId).orElseThrow(() -> new ResourceNotFoundException("Academic year"));
        LocalDate today = schoolTime.today(schoolId);
        List<Object> args = new ArrayList<>(List.of(today, schoolId, yearId));
        String grade = "";
        if (gradeId != null) {
            grade = " and fs.grade_level_id = ?";
            args.add(gradeId);
        }
        List<FeeDtos.CategoryTotals> byCategory = new ArrayList<>();
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("select sf.category_name, "
                + "coalesce(sum(case when not sf.waived then sf.amount else 0 end), 0), "
                + "coalesce(sum(case when not sf.waived then sf.paid_amount else 0 end), 0), "
                + "coalesce(sum(case when not sf.waived and sf.due_date < ? then sf.amount - sf.paid_amount "
                + "else 0 end), 0), coalesce(sum(case when sf.waived then sf.amount else 0 end), 0) "
                + "from student_fees sf join fee_structures fs on fs.id = sf.fee_structure_id "
                + "where sf.school_id = ? and sf.academic_year_id = ?" + grade
                + " group by sf.category_name order by sf.category_name", rs -> {
                    BigDecimal billed = rs.getBigDecimal(2);
                    BigDecimal collected = rs.getBigDecimal(3);
                    byCategory.add(new FeeDtos.CategoryTotals(rs.getString(1), billed, collected,
                            billed.subtract(collected)));
                    totals[0] = totals[0].add(billed);
                    totals[1] = totals[1].add(collected);
                    totals[2] = totals[2].add(rs.getBigDecimal(4));
                    totals[3] = totals[3].add(rs.getBigDecimal(5));
                }, args.toArray());

        Map<FeeStatus, Long> counts = new EnumMap<>(FeeStatus.class);
        for (FeeStatus status : FeeStatus.values()) {
            counts.put(status, fees.count(StudentFeeService.inScope(schoolId, yearId, gradeId)
                    .and(FeeStatusRules.matching(status, today, properties.dueSoonDays()))));
        }
        return new FeeDtos.Summary(yearId, gradeId, totals[0], totals[1], totals[0].subtract(totals[1]), totals[2],
                totals[3], counts, byCategory);
    }

    @Transactional(readOnly = true)
    public FeeDtos.Collections collections(UUID schoolId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? schoolTime.today(schoolId) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (end.isBefore(start)) {
            throw new BusinessRuleException("The end date cannot be before the start date.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_PERIOD_DAYS) {
            throw new BusinessRuleException("Choose a period of at most one year.");
        }
        List<FeeDtos.MethodTotal> byMethod = new ArrayList<>();
        BigDecimal[] totals = {BigDecimal.ZERO, BigDecimal.ZERO};
        long[] counts = {0};
        jdbc.query("select p.method, count(*), coalesce(sum(p.amount), 0), count(r.id), "
                + "coalesce(sum(case when r.id is not null then p.amount else 0 end), 0) "
                + "from payments p left join payment_reversals r on r.payment_id = p.id "
                + "where p.school_id = ? and p.paid_on between ? and ? group by p.method order by p.method", rs -> {
                    long all = rs.getLong(2);
                    long undone = rs.getLong(4);
                    BigDecimal received = rs.getBigDecimal(3);
                    BigDecimal reversed = rs.getBigDecimal(5);
                    byMethod.add(new FeeDtos.MethodTotal(PaymentMethod.valueOf(rs.getString(1)), all - undone,
                            received.subtract(reversed)));
                    totals[0] = totals[0].add(received);
                    totals[1] = totals[1].add(reversed);
                    counts[0] += all;
                }, schoolId, start, end);
        return new FeeDtos.Collections(start, end, counts[0], totals[0], totals[1], totals[0].subtract(totals[1]),
                byMethod);
    }
}
