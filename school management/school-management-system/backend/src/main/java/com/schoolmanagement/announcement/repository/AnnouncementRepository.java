package com.schoolmanagement.announcement.repository;

import com.schoolmanagement.announcement.entity.Announcement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnnouncementRepository
        extends JpaRepository<Announcement, UUID>, JpaSpecificationExecutor<Announcement> {

    @EntityGraph(attributePaths = {"section", "section.gradeLevel"})
    Optional<Announcement> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Override
    @EntityGraph(attributePaths = {"section", "section.gradeLevel"})
    Page<Announcement> findAll(Specification<Announcement> spec, Pageable pageable);

    boolean existsBySectionId(UUID sectionId);
}
