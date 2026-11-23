package com.schoolmanagement.announcement.service;

import com.schoolmanagement.academic.entity.Section;
import com.schoolmanagement.academic.repository.SectionRepository;
import com.schoolmanagement.academic.repository.SectionSubjectRepository;
import com.schoolmanagement.announcement.dto.AnnouncementDtos;
import com.schoolmanagement.announcement.entity.Announcement;
import com.schoolmanagement.announcement.entity.AnnouncementAudience;
import com.schoolmanagement.announcement.entity.AnnouncementRead;
import com.schoolmanagement.announcement.entity.AnnouncementStatus;
import com.schoolmanagement.announcement.repository.AnnouncementReadRepository;
import com.schoolmanagement.announcement.repository.AnnouncementRepository;
import com.schoolmanagement.audit.AuditEventPublisher;
import com.schoolmanagement.common.api.PageResponse;
import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.exception.FieldValidationException;
import com.schoolmanagement.common.exception.ResourceNotFoundException;
import com.schoolmanagement.common.persistence.OptimisticVersion;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.security.AuthenticatedUser;
import com.schoolmanagement.student.repository.StudentEnrollmentRepository;
import com.schoolmanagement.student.repository.StudentProfileRepository;
import com.schoolmanagement.teacher.service.TeacherDirectory;
import com.schoolmanagement.user.entity.RoleName;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Announcements: authoring (administrators for anyone, teachers only for classes they teach), and the feed each
 * person reads. What someone sees is decided here from their roles and classes, never from the request.
 */
@Service
public class AnnouncementService {

    private static final String ENTITY = "Announcement";

    private final AnnouncementRepository announcements;
    private final AnnouncementReadRepository reads;
    private final SectionRepository sections;
    private final SectionSubjectRepository sectionSubjects;
    private final StudentProfileRepository students;
    private final StudentEnrollmentRepository enrollments;
    private final TeacherDirectory directory;
    private final AuditEventPublisher audit;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository announcements, AnnouncementReadRepository reads,
            SectionRepository sections, SectionSubjectRepository sectionSubjects, StudentProfileRepository students,
            StudentEnrollmentRepository enrollments, TeacherDirectory directory, AuditEventPublisher audit,
            Clock clock) {
        this.announcements = announcements;
        this.reads = reads;
        this.sections = sections;
        this.sectionSubjects = sectionSubjects;
        this.students = students;
        this.enrollments = enrollments;
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- who belongs to which audience -----------------------------------------------------------------

    private record Audience(boolean admin, boolean teacher, boolean student, Set<UUID> sectionIds) {

        List<AnnouncementAudience> plain() {
            List<AnnouncementAudience> list = new ArrayList<>(List.of(AnnouncementAudience.ALL));
            if (admin) {
                list.add(AnnouncementAudience.ADMINS);
            }
            if (teacher) {
                list.add(AnnouncementAudience.TEACHERS);
            }
            if (student) {
                list.add(AnnouncementAudience.STUDENTS);
            }
            return list;
        }

        boolean includes(Announcement a) {
            return a.getAudience() == AnnouncementAudience.SECTION
                    ? a.getSection() != null && sectionIds.contains(a.getSection().getId())
                    : plain().contains(a.getAudience());
        }
    }

    private Audience audienceOf(AuthenticatedUser actor) {
        boolean admin = actor.roles().contains(RoleName.ROLE_ADMIN.name());
        boolean teacher = actor.roles().contains(RoleName.ROLE_TEACHER.name());
        boolean student = actor.roles().contains(RoleName.ROLE_STUDENT.name());
        Set<UUID> sectionIds = new HashSet<>();
        if (teacher) {
            sectionSubjects.findAllByTeacherIdAndActiveTrueAndSchoolId(actor.id(), actor.schoolId())
                    .forEach(a -> sectionIds.add(a.getSection().getId()));
            sections.findAllByClassTeacherIdAndSchoolId(actor.id(), actor.schoolId())
                    .forEach(s -> sectionIds.add(s.getId()));
        }
        if (student) {
            students.findByUserId(actor.id()).filter(p -> p.getSchoolId().equals(actor.schoolId()))
                    .ifPresent(p -> enrollments.findAllActiveByStudentId(p.getId())
                            .forEach(e -> sectionIds.add(e.getSection().getId())));
        }
        return new Audience(admin, teacher, student, sectionIds);
    }

    // ---- reading ----------------------------------------------------------------------------------------

    private Specification<Announcement> feedSpec(AuthenticatedUser actor, Audience audience, boolean unreadOnly) {
        Instant now = clock.instant();
        return (root, query, cb) -> {
            List<Predicate> forMe = new ArrayList<>();
            forMe.add(root.get("audience").in(audience.plain()));
            if (!audience.sectionIds().isEmpty()) {
                forMe.add(cb.and(cb.equal(root.get("audience"), AnnouncementAudience.SECTION),
                        root.get("section").get("id").in(audience.sectionIds())));
            }
            List<Predicate> all = new ArrayList<>(List.of(cb.equal(root.get("schoolId"), actor.schoolId()),
                    cb.equal(root.get("status"), AnnouncementStatus.PUBLISHED),
                    cb.or(cb.isNull(root.get("publishAt")), cb.lessThanOrEqualTo(root.<Instant>get("publishAt"), now)),
                    cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThan(root.<Instant>get("expiresAt"), now)),
                    cb.or(forMe.toArray(new Predicate[0]))));
            if (unreadOnly) {
                Subquery<UUID> read = query.subquery(UUID.class);
                Root<AnnouncementRead> r = read.from(AnnouncementRead.class);
                read.select(r.get("id")).where(cb.equal(r.get("announcementId"), root.get("id")),
                        cb.equal(r.get("userId"), actor.id()));
                all.add(cb.not(cb.exists(read)));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementDtos.Response> feed(AuthenticatedUser actor, boolean unreadOnly,
            Pageable pageable) {
        Page<Announcement> page = announcements.findAll(feedSpec(actor, audienceOf(actor), unreadOnly), pageable);
        Set<UUID> read = readIds(actor, page.getContent());
        Map<UUID, String> authors = authors(page.getContent());
        Instant now = clock.instant();
        return PageResponse.from(page, a -> view(a, authors, null, read.contains(a.getId()), now));
    }

    @Transactional(readOnly = true)
    public AnnouncementDtos.UnreadCount unreadCount(AuthenticatedUser actor) {
        return new AnnouncementDtos.UnreadCount(announcements.count(feedSpec(actor, audienceOf(actor), true)));
    }

    @Transactional(readOnly = true)
    public AnnouncementDtos.Response getVisible(AuthenticatedUser actor, UUID id) {
        Announcement announcement = visible(actor, id);
        return view(announcement, authors(List.of(announcement)), null,
                readIds(actor, List.of(announcement)).contains(id), clock.instant());
    }

    @Transactional
    public void markRead(AuthenticatedUser actor, UUID id) {
        visible(actor, id);
        reads.markRead(id, actor.id());
    }

    private Announcement visible(AuthenticatedUser actor, UUID id) {
        Announcement announcement = announcements.findByIdAndSchoolId(id, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY));
        if (!announcement.isLive(clock.instant()) || !audienceOf(actor).includes(announcement)) {
            throw new ResourceNotFoundException(ENTITY);
        }
        return announcement;
    }

    private Set<UUID> readIds(AuthenticatedUser actor, List<Announcement> list) {
        return list.isEmpty() ? Set.of() : new HashSet<>(reads.findReadIds(actor.id(),
                list.stream().map(Announcement::getId).toList()));
    }

    // ---- managing ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementDtos.Response> manage(AuthenticatedUser actor, AnnouncementStatus status,
            AnnouncementAudience audience, String search, Pageable pageable) {
        Specification<Announcement> spec = Specification
                .<Announcement>where(SearchSpecs.equalTo("schoolId", actor.schoolId()))
                .and(SearchSpecs.equalTo("status", status)).and(SearchSpecs.equalTo("audience", audience))
                .and(SearchSpecs.containsIgnoreCase("title", search));
        if (!isAdmin(actor)) {
            spec = spec.and(SearchSpecs.equalTo("createdBy", actor.id()));
        }
        Page<Announcement> page = announcements.findAll(spec, pageable);
        Map<UUID, Long> counts = readCounts(page.getContent());
        Map<UUID, String> authors = authors(page.getContent());
        Instant now = clock.instant();
        return PageResponse.from(page, a -> view(a, authors, counts.getOrDefault(a.getId(), 0L), false, now));
    }

    @Transactional(readOnly = true)
    public AnnouncementDtos.Response getManaged(AuthenticatedUser actor, UUID id) {
        return managedView(owned(actor, id));
    }

    @Transactional
    public AnnouncementDtos.Response create(AuthenticatedUser actor, AnnouncementDtos.Request request) {
        Section section = target(actor, request.audience(), request.sectionId());
        checkWindow(request.publishAt(), request.expiresAt());
        Announcement announcement = announcements.saveAndFlush(new Announcement(actor.schoolId(), actor.id(),
                request.title().trim(), request.body().trim(), request.audience(), section, request.publishAt(),
                request.expiresAt()));
        audit.record(actor, "ANNOUNCEMENT_CREATED", ENTITY, announcement.getId(), describe(announcement));
        return managedView(announcement);
    }

    @Transactional
    public AnnouncementDtos.Response update(AuthenticatedUser actor, UUID id, AnnouncementDtos.Request request) {
        Announcement announcement = owned(actor, id);
        OptimisticVersion.check(request.version(), announcement.getVersion());
        if (announcement.getStatus() == AnnouncementStatus.ARCHIVED) {
            throw new BusinessRuleException("An archived announcement cannot be changed.");
        }
        boolean sameTarget = announcement.getAudience() == request.audience() && java.util.Objects.equals(
                announcement.getSection() == null ? null : announcement.getSection().getId(), request.sectionId());
        if (announcement.getStatus() == AnnouncementStatus.DRAFT) {
            if (!sameTarget) {
                announcement.retarget(request.audience(), target(actor, request.audience(), request.sectionId()));
            }
        } else if (!sameTarget) {
            throw new BusinessRuleException("A published announcement cannot change its audience. Archive it and "
                    + "write a new one.");
        }
        checkWindow(request.publishAt(), request.expiresAt());
        announcement.edit(request.title().trim(), request.body().trim(), request.publishAt(), request.expiresAt());
        announcements.saveAndFlush(announcement);
        audit.record(actor, "ANNOUNCEMENT_UPDATED", ENTITY, id, describe(announcement));
        return managedView(announcement);
    }

    @Transactional
    public AnnouncementDtos.Response publish(AuthenticatedUser actor, UUID id) {
        Announcement announcement = owned(actor, id);
        Instant now = clock.instant();
        if (announcement.getExpiresAt() != null && !announcement.getExpiresAt().isAfter(now)) {
            throw new BusinessRuleException("This announcement has already expired. Change its expiry before publishing.");
        }
        announcement.publish(now);
        return transitioned(actor, announcement, "ANNOUNCEMENT_PUBLISHED");
    }

    @Transactional
    public AnnouncementDtos.Response archive(AuthenticatedUser actor, UUID id) {
        Announcement announcement = owned(actor, id);
        announcement.archive();
        return transitioned(actor, announcement, "ANNOUNCEMENT_ARCHIVED");
    }

    /** Only a draft can be deleted; once it was published people may have read it, so it is archived instead. */
    @Transactional
    public void delete(AuthenticatedUser actor, UUID id) {
        Announcement announcement = owned(actor, id);
        if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
            throw new BusinessRuleException("Only a draft can be deleted. Archive a published announcement instead.");
        }
        announcements.delete(announcement);
        audit.record(actor, "ANNOUNCEMENT_DELETED", ENTITY, id, describe(announcement));
    }

    private AnnouncementDtos.Response transitioned(AuthenticatedUser actor, Announcement announcement, String action) {
        announcements.saveAndFlush(announcement);
        audit.record(actor, action, ENTITY, announcement.getId(), describe(announcement));
        return managedView(announcement);
    }

    /** Administrators reach every announcement of the school; a teacher only their own. */
    private Announcement owned(AuthenticatedUser actor, UUID id) {
        return announcements.findByIdAndSchoolId(id, actor.schoolId())
                .filter(a -> isAdmin(actor) || a.getCreatedBy().equals(actor.id()))
                .orElseThrow(() -> new ResourceNotFoundException(ENTITY));
    }

    private static boolean isAdmin(AuthenticatedUser actor) {
        return actor.roles().contains(RoleName.ROLE_ADMIN.name());
    }

    /** The class an announcement is for (null for the other audiences), checking that the author may address it. */
    private Section target(AuthenticatedUser actor, AnnouncementAudience audience, UUID sectionId) {
        if (audience != AnnouncementAudience.SECTION) {
            if (sectionId != null) {
                throw new FieldValidationException("sectionId", "A class can only be chosen for a class announcement");
            }
            if (!isAdmin(actor)) {
                throw new AccessDeniedException("Teachers can only write to their own classes");
            }
            return null;
        }
        if (sectionId == null) {
            throw new FieldValidationException("sectionId", "Choose a class");
        }
        Section section = sections.findByIdAndSchoolId(sectionId, actor.schoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Section"));
        if (!isAdmin(actor) && !teachesSection(actor, section)) {
            throw new AccessDeniedException("Not a teacher of this class");
        }
        if (section.getAcademicYear().isClosed()) {
            throw new BusinessRuleException("This class belongs to a closed academic year.");
        }
        return section;
    }

    private boolean teachesSection(AuthenticatedUser actor, Section section) {
        return actor.id().equals(section.getClassTeacherId())
                || sectionSubjects.existsBySectionIdAndTeacherIdAndActiveTrue(section.getId(), actor.id());
    }

    private static void checkWindow(Instant publishAt, Instant expiresAt) {
        if (publishAt != null && expiresAt != null && !expiresAt.isAfter(publishAt)) {
            throw new FieldValidationException("expiresAt", "The expiry must be after the start");
        }
    }

    // ---- mapping ----------------------------------------------------------------------------------------

    private AnnouncementDtos.Response managedView(Announcement announcement) {
        Map<UUID, Long> counts = readCounts(List.of(announcement));
        return view(announcement, authors(List.of(announcement)), counts.getOrDefault(announcement.getId(), 0L), false,
                clock.instant());
    }

    private Map<UUID, Long> readCounts(List<Announcement> list) {
        Map<UUID, Long> counts = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : reads.countByAnnouncement(list.stream().map(Announcement::getId).toList())) {
                counts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    private Map<UUID, String> authors(List<Announcement> list) {
        return directory.displayNames(list.stream().map(Announcement::getCreatedBy).collect(Collectors.toSet()));
    }

    private static AnnouncementDtos.Response view(Announcement a, Map<UUID, String> authors, Long readCount,
            boolean read, Instant now) {
        Section section = a.getSection();
        return new AnnouncementDtos.Response(a.getId(), a.getTitle(), a.getBody(), a.getAudience(),
                section == null ? null : section.getId(),
                section == null ? null : section.getGradeLevel().getName() + " - " + section.getName(), a.getStatus(),
                a.getPublishAt(), a.getExpiresAt(), a.getPublishedAt(), authors.get(a.getCreatedBy()),
                a.getCreatedAt(), readCount, read, a.isLive(now), a.getVersion());
    }

    private static String describe(Announcement a) {
        return "Announcement \"" + a.getTitle() + "\" for " + a.getAudience() + " (" + a.getStatus() + ")";
    }
}
