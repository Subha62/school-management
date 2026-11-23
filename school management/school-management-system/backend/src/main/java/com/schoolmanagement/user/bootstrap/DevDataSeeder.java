package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.service.UserProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Development-only demo data. Refuses to run unless the database is on the local machine, so a
 * misconfigured deployment can never receive well-known demo credentials.
 */
@Component
@Profile("dev")
@Order(1)
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    static final String SCHOOL_CODE = "DEMO";

    private final SchoolRepository schools;
    private final UserProvisioner provisioner;
    private final String datasourceUrl;
    private final String demoPassword;

    DevDataSeeder(SchoolRepository schools, UserProvisioner provisioner,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.provisioner = provisioner;
        this.datasourceUrl = datasourceUrl;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword.isBlank() || !isLocalDatabase()) {
            log.info("Demo data seeding skipped (disabled or database is not local)");
            return;
        }
        if (schools.findBySchoolCode(SCHOOL_CODE).isPresent()) {
            return;
        }
        School school = schools.save(new School("Demo School", SCHOOL_CODE, "Asia/Kolkata"));
        provisioner.createActiveUser(school.getId(), "admin", "admin@demo.school", demoPassword, "Asha", "Admin",
                RoleName.ROLE_ADMIN);
        provisioner.createActiveUser(school.getId(), "T-0001", "teacher@demo.school", demoPassword, "Tara",
                "Teacher", RoleName.ROLE_TEACHER);
        provisioner.createActiveUser(school.getId(), "S-0001", "student@demo.school", demoPassword, "Sam",
                "Student", RoleName.ROLE_STUDENT);
        log.info("Seeded demo school with admin, teacher and student accounts (development only)");
    }

    private boolean isLocalDatabase() {
        return LocalDatabase.isLocal(datasourceUrl);
    }
}
