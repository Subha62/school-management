package com.schoolmanagement.announcement.repository;

import com.schoolmanagement.announcement.entity.AnnouncementRead;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementReadRepository extends JpaRepository<AnnouncementRead, UUID> {

    /** Marks as read; reading twice, even at the same moment, changes nothing. */
    @Modifying
    @Query(value = "insert into announcement_reads (id, announcement_id, user_id, read_at, created_at, updated_at, "
            + "version) values (gen_random_uuid(), :announcementId, :userId, now(), now(), now(), 0) "
            + "on conflict (announcement_id, user_id) do nothing", nativeQuery = true)
    int markRead(@Param("announcementId") UUID announcementId, @Param("userId") UUID userId);

    @Query("select r.announcementId from AnnouncementRead r where r.userId = :userId "
            + "and r.announcementId in :announcementIds")
    List<UUID> findReadIds(@Param("userId") UUID userId, @Param("announcementIds") Collection<UUID> announcementIds);

    @Query("select r.announcementId, count(r) from AnnouncementRead r where r.announcementId in :announcementIds "
            + "group by r.announcementId")
    List<Object[]> countByAnnouncement(@Param("announcementIds") Collection<UUID> announcementIds);
}
