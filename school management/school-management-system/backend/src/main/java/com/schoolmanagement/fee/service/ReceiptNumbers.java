package com.schoolmanagement.fee.service;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipt numbers such as {@code REC-2026-000042}: sequential per school and year. The counter row is updated in
 * the same transaction as the payment, so a payment that fails to save does not use up a number, and two cashiers
 * can never get the same one (the update locks the row until commit).
 */
@Component
public class ReceiptNumbers {

    private final JdbcTemplate jdbc;

    public ReceiptNumbers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(UUID schoolId, LocalDate date) {
        int year = date.getYear();
        jdbc.update("insert into receipt_counters (school_id, receipt_year, last_number) values (?, ?, 0) "
                + "on conflict (school_id, receipt_year) do nothing", schoolId, year);
        Integer number = jdbc.queryForObject("update receipt_counters set last_number = last_number + 1 "
                + "where school_id = ? and receipt_year = ? returning last_number", Integer.class, schoolId, year);
        return String.format("REC-%d-%06d", year, number);
    }
}
