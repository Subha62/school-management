package com.schoolmanagement.user.repository;

import com.schoolmanagement.user.entity.Role;
import com.schoolmanagement.user.entity.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
