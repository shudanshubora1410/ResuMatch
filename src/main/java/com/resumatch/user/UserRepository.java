package com.resumatch.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByDisplayName(String displayName);
    List<UserEntity> findByRole(com.resumatch.common.Role role);
}
