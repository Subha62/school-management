package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.auth.validation.StrongPasswordValidator;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.repository.UserRepository;
import com.schoolmanagement.user.service.UserProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first school and administrator from environment variables when no active admin exists.
 * Both variables must be supplied, so nothing is created by default. Remove the variables after first start.
 */
@Component
class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final SchoolRepository schools;
    private final UserRepository users;
    private final UserProvisioner provisioner;
    private final String adminEmail;
    private final String adminPassword;
    private final String schoolName;
    private final String schoolCode;

    InitialAdminBootstrap(SchoolRepository schools, UserRepository users, UserProvisioner provisioner,
            @Value("${app.bootstrap.admin-email:}") String adminEmail,
            @Value("${app.bootstrap.admin-password:}") String adminPassword,
            @Value("${app.bootstrap.school-name:My School}") String schoolName,
            @Value("${app.bootstrap.school-code:SCHOOL}") String schoolCode) {
        this.schools = schools;
        this.users = users;
        this.provisioner = provisioner;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.schoolName = schoolName;
        this.schoolCode = schoolCode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (users.existsActiveWithRole(RoleName.ROLE_ADMIN)) {
            log.info("Initial admin bootstrap skipped: an active administrator already exists");
            return;
        }
        if (!new StrongPasswordValidator().isValid(adminPassword, null)) {
            throw new IllegalStateException(
                    "app.bootstrap.admin-password does not meet the password policy (10-72 characters with upper "
                            + "case, lower case, digit and special character)");
        }
        School school = schools.findBySchoolCode(schoolCode)
                .orElseGet(() -> schools.save(new School(schoolName, schoolCode, "UTC")));
        provisioner.createActiveUser(school.getId(), null, adminEmail.trim(), adminPassword, "School", "Administrator",
                RoleName.ROLE_ADMIN);
        log.info("Created the initial administrator account. Remove the bootstrap variables now.");
    }
}
