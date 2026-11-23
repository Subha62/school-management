package com.schoolmanagement.school.service;

import com.schoolmanagement.school.repository.SchoolRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** "Today" as the school lives it: calendar dates (due dates, payment dates) follow the school's time zone. */
@Component
public class SchoolTime {

    private final SchoolRepository schools;
    private final Clock clock;

    public SchoolTime(SchoolRepository schools, Clock clock) {
        this.schools = schools;
        this.clock = clock;
    }

    public LocalDate today(UUID schoolId) {
        return LocalDate.now(clock.withZone(zone(schoolId)));
    }

    public ZoneId zone(UUID schoolId) {
        return schools.findById(schoolId).map(school -> zoneOf(school.getTimezone())).orElse(ZoneId.of("UTC"));
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            return ZoneId.of("UTC");
        }
    }
}
