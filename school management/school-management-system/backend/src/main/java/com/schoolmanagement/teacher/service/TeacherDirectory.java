package com.schoolmanagement.teacher.service;

import com.schoolmanagement.common.exception.BusinessRuleException;
import com.schoolmanagement.common.persistence.SearchSpecs;
import com.schoolmanagement.teacher.entity.EmploymentStatus;
import com.schoolmanagement.teacher.repository.TeacherProfileRepository;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looks up teacher accounts for features that assign teachers (sections, subjects, later timetables). */
@Service
public class TeacherDirectory {

    private final UserRepository users;
    private final TeacherProfileRepository profiles;

    public TeacherDirectory(UserRepository users, TeacherProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    /**
     * Fails unless the user is an active teacher of the given school. A teacher who has a profile must also be
     * actively employed (not on leave, resigned or retired).
     */
    @Transactional(readOnly = true)
    public User requireActiveTeacher(UUID schoolId, UUID userId) {
        User teacher = users.findWithRolesById(userId)
                .filter(user -> user.getSchoolId().equals(schoolId))
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> user.hasRole(RoleName.ROLE_TEACHER))
                .orElseThrow(() -> new BusinessRuleException("The selected teacher is not available."));
        boolean employed = profiles.findByUserId(userId)
                .map(profile -> profile.getEmploymentStatus() == EmploymentStatus.ACTIVE).orElse(true);
        if (!employed) {
            throw new BusinessRuleException("The selected teacher is not available.");
        }
        return teacher;
    }

    @Transactional(readOnly = true)
    public Page<User> searchActiveTeachers(UUID schoolId, String search, Pageable pageable) {
        return users.searchActiveByRole(schoolId, RoleName.ROLE_TEACHER, SearchSpecs.likePattern(search), pageable);
    }

    /** Display names (first and last) keyed by user id, loaded in one query. */
    @Transactional(readOnly = true)
    public Map<UUID, String> displayNames(Collection<UUID> userIds) {
        Map<UUID, String> names = new HashMap<>();
        if (userIds.isEmpty()) {
            return names;
        }
        users.findAllById(userIds).forEach(user -> names.put(user.getId(),
                (user.getFirstName() + " " + user.getLastName()).trim()));
        return names;
    }
}
