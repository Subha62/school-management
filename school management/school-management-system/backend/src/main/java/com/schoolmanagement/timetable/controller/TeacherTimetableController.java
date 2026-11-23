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

/** The signed-in teacher's own timetable. There is no id in the path, so there is nothing to tamper with. */
@RestController
@RequestMapping("/api/v1/teacher/timetable")
@PreAuthorize("hasAuthority('ROLE_TEACHER')")
@Tag(name = "Teacher timetable")
public class TeacherTimetableController {

    private final TimetableService service;

    public TeacherTimetableController(TimetableService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My weekly timetable (the week containing `date`, default this week)")
    public TimetableDtos.Week myWeek(@AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.ownWeek(actor.schoolId(), actor.id(), date);
    }
}
