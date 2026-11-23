package com.schoolmanagement.user.service;

import com.schoolmanagement.common.exception.ConflictException;
import com.schoolmanagement.user.entity.AccountStatus;
import com.schoolmanagement.user.entity.Role;
import com.schoolmanagement.user.entity.RoleName;
import com.schoolmanagement.user.entity.User;
import com.schoolmanagement.user.repository.RoleRepository;
import com.schoolmanagement.user.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates accounts with a hashed password and a single role. Reused by bootstrap and, later, admin screens. */
@Service
public class UserProvisioner {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioner(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createActiveUser(UUID schoolId, String username, String email, String rawPassword,
            String firstName, String lastName, RoleName roleName) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A user with this email already exists.");
        }
        Role role = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " is missing from the database"));
        User user = new User(schoolId, username, email, passwordEncoder.encode(rawPassword), firstName, lastName,
                AccountStatus.ACTIVE);
        user.addRole(role);
        return users.save(user);
    }
}
