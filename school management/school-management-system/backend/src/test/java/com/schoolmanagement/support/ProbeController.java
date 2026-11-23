package com.schoolmanagement.support;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;

/** Endpoints that exist only to exercise the shared error handling and security rules. */
@RestController
@RequestMapping("/api/v1/probe")
public class ProbeController {

    public record NamePayload(@NotBlank(message = "Name is required") String name) {
    }

    @PostMapping("/validated")
    public NamePayload validated(@Valid @RequestBody NamePayload payload) {
        return payload;
    }

    @GetMapping("/size")
    public String size(@RequestParam @Min(value = 1, message = "Size must be at least 1") int size) {
        return "ok";
    }

    @GetMapping("/missing")
    public String missing() {
        throw new ResourceNotFoundException("Student");
    }

    @GetMapping("/rule")
    public String rule() {
        throw new BusinessRuleException("Marks cannot exceed the maximum marks.");
    }

    @GetMapping("/stale")
    public String stale() {
        throw new ObjectOptimisticLockingFailureException("Student", "id-1");
    }

    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("select * from users where password = 'secret'");
    }
}
