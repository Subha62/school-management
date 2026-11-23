package com.schoolmanagement.common.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System")
public class SystemController {

    private final String applicationName;
    private final Environment environment;

    public SystemController(@Value("${spring.application.name}") String applicationName, Environment environment) {
        this.applicationName = applicationName;
        this.environment = environment;
    }

    @GetMapping("/info")
    @Operation(summary = "Public application status used by the frontend shell")
    public SystemInfoResponse info() {
        String[] profiles = environment.getActiveProfiles();
        return new SystemInfoResponse(applicationName, "UP", profiles.length == 0 ? null : profiles[0]);
    }

    public record SystemInfoResponse(String name, String status, String profile) {
    }
}
