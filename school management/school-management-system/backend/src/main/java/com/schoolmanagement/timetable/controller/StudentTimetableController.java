package com.schoolmanagement.timetable.controller;

import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.timetable.dto.TimetableDtos;
import com.schoolmanagement.timetable.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in student's weekly timetable, taken from the section they are enrolled in. */
@RestController
@RequestMapping("/api/v1/student/timetable")
@PreAuthorize("hasAuthority('ROLE_STUDENT')")
@Tag(name = "Student timetable")
public class StudentTimetableController {

    private final TimetableService service;

    public StudentTimetableController(TimetableService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My weekly timetable (the week containing `date`, default this week)")
    public TimetableDtos.Week myWeek(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.studentWeek(actor.schoolId(), actor.id(), date);
    }
}
