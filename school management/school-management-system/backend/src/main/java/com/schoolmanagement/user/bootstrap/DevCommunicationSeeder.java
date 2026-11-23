package com.schoolmanagement.user.bootstrap;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.announcement.dto.AnnouncementDtos;
import com.schoolmanagement.announcement.entity.AnnouncementAudience;
import com.schoolmanagement.announcement.repository.AnnouncementRepository;
import com.schoolmanagement.announcement.service.AnnouncementService;
import com.schoolmanagement.fee.dto.FeeDtos;
import com.schoolmanagement.fee.entity.PaymentMethod;
import com.schoolmanagement.fee.repository.FeeCategoryRepository;
import com.schoolmanagement.fee.service.FeeConfigService;
import com.schoolmanagement.fee.service.PaymentService;
import com.schoolmanagement.fee.service.StudentFeeService;
import com.schoolmanagement.school.entity.School;
import com.schoolmanagement.school.repository.SchoolRepository;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Development-only: a few announcements for each audience, and tuition and transport fees for the demo class with
 * one payment and one overdue bill. Goes through the real services, so the demo data obeys the same rules. Runs only
 * against a local database and only once.
 */
@Component
@Profile("dev")
@Order(7)
class DevCommunicationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevCommunicationSeeder.class);

    private final SchoolRepository schools;
    private final SectionRepository sections;
    private final UserRepository users;
    private final AnnouncementRepository announcements;
    private final AnnouncementService announcementService;
    private final FeeCategoryRepository categories;
    private final FeeConfigService feeConfig;
    private final StudentFeeService studentFees;
    private final PaymentService paymentService;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String datasourceUrl;
    private final String demoPassword;

    DevCommunicationSeeder(SchoolRepository schools, SectionRepository sections, UserRepository users,
            AnnouncementRepository announcements, AnnouncementService announcementService,
            FeeCategoryRepository categories, FeeConfigService feeConfig, StudentFeeService studentFees,
            PaymentService paymentService, TransactionTemplate transaction, Clock clock,
            @Value("${spring.datasource.url}") String datasourceUrl,
            @Value("${app.seed.demo-password:}") String demoPassword) {
        this.schools = schools;
        this.sections = sections;
        this.users = users;
        this.announcements = announcements;
        this.announcementService = announcementService;
        this.categories = categories;
        this.feeConfig = feeConfig;
        this.studentFees = studentFees;
        this.paymentService = paymentService;
        this.transaction = transaction;
        this.clock = clock;
        this.datasourceUrl = datasourceUrl;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword.isBlank() || !LocalDatabase.isLocal(datasourceUrl)) {
            return;
        }
        schools.findBySchoolCode(DevDataSeeder.SCHOOL_CODE)
                .ifPresent(school -> transaction.executeWithoutResult(status -> seed(school)));
    }

    private void seed(School school) {
        Section section = sections.findAll().stream().findFirst().orElse(null);
        User admin = users.findByLoginIdentifier("admin@demo.school").stream().findFirst().orElse(null);
        if (section == null || admin == null) {
            return;
        }
        AuthenticatedUser actor = AuthenticatedUser.from(admin);
        if (announcements.count() == 0) {
            announcements(actor, section);
        }
        if (categories.count() == 0) {
            fees(actor, section);
        }
        log.info("Seeded demo announcements and fees (development only)");
    }

    private void announcements(AuthenticatedUser actor, Section section) {
        publish(actor, "Welcome back", "Classes resume on Monday. Please check the timetable for your section.",
                AnnouncementAudience.ALL, null, null);
        publish(actor, "Staff meeting", "Staff meeting on Friday at 3 pm in the library. Bring your mark sheets.",
                AnnouncementAudience.TEACHERS, null, null);
        publish(actor, "Sports day", "Sports day is on the last Saturday of the month. Wear your house colours.",
                AnnouncementAudience.STUDENTS, null, clock.instant().plus(Duration.ofDays(30)));
        publish(actor, "Class photograph", "The class photograph will be taken on Wednesday morning. Please come in "
                + "full uniform.", AnnouncementAudience.SECTION, section, null);
        announcementService.create(actor, new AnnouncementDtos.Request("Draft: exam timetable",
                "The Term 1 timetable will be published here.", AnnouncementAudience.ALL, null, null, null, null));
    }

    private void publish(AuthenticatedUser actor, String title, String body, AnnouncementAudience audience,
            Section section, Instant expiresAt) {
        var created = announcementService.create(actor, new AnnouncementDtos.Request(title, body, audience,
                section == null ? null : section.getId(), null, expiresAt, null));
        announcementService.publish(actor, created.id());
    }

    private void fees(AuthenticatedUser actor, Section section) {
        var tuition = feeConfig.createCategory(actor, new FeeDtos.CategoryRequest("Tuition", "Term tuition", true, null));
        var transport = feeConfig.createCategory(actor, new FeeDtos.CategoryRequest("Transport", "School bus", true,
                null));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var tuitionFee = feeConfig.createStructure(actor, new FeeDtos.StructureRequest(
                section.getAcademicYear().getId(), section.getGradeLevel().getId(), tuition.getId(), "Term 1 tuition",
                new BigDecimal("15000.00"), today.plusDays(20), true, null));
        var transportFee = feeConfig.createStructure(actor, new FeeDtos.StructureRequest(
                section.getAcademicYear().getId(), section.getGradeLevel().getId(), transport.getId(),
                "Bus fee, term 1", new BigDecimal("3000.00"), today.minusDays(5), true, null));
        feeConfig.generate(actor, tuitionFee.id());
        feeConfig.generate(actor, transportFee.id());

        var firstPage = studentFees.search(actor.schoolId(), section.getAcademicYear().getId(), null, null, null, null,
                null, org.springframework.data.domain.PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(
                        "createdAt")));
        firstPage.content().stream().filter(f -> f.categoryName().equals("Tuition")).findFirst()
                .ifPresent(f -> paymentService.record(actor, f.id(), new FeeDtos.PaymentRequest(
                        new BigDecimal("5000.00"), PaymentMethod.UPI, "UPI-DEMO-0001", today)));
    }
}
